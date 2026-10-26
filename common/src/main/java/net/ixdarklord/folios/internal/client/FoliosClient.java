package net.ixdarklord.folios.internal.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosStyles;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.internal.FoliosHooks;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.ixdarklord.folios.internal.client.page.EmptyPage;
import net.ixdarklord.folios.internal.client.page.EntityPageElement;
import net.ixdarklord.folios.internal.client.page.ImagePageElement;
import net.ixdarklord.folios.internal.client.page.LinksPageElement;
import net.ixdarklord.folios.internal.client.page.MultiblockPageElement;
import net.ixdarklord.folios.internal.client.page.QuestPageElement;
import net.ixdarklord.folios.internal.client.page.RecipePageElement;
import net.ixdarklord.folios.internal.client.page.SpotlightPageElement;
import net.ixdarklord.folios.internal.client.page.TextPage;
import net.ixdarklord.folios.internal.client.screen.FoliosScreen;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.mixin.client.ItemModelsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

// Sets up guide books on the client: the kinds of page and book, the item's model, and what opens a book.
public final class FoliosClient {
    // A book a command asked for, opened at the next tick (the chat closing would otherwise close it).
    private static @Nullable Runnable pending;

    private FoliosClient() {}

    public static void init() {
        net.ixdarklord.folios.internal.config.FoliosClientConfig.CONFIG.id();
        FoliosPageElements.register(Folios.rl("text"), TextPage.CODEC);
        FoliosPageElements.register(Folios.rl("spotlight"), SpotlightPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("image"), ImagePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("recipe"), RecipePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("entity"), EntityPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("multiblock"), MultiblockPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("structure"), net.ixdarklord.folios.internal.client.page.StructurePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("build"), net.ixdarklord.folios.internal.client.page.BuildPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("links"), LinksPageElement.CODEC);
        // Links showing an entity, a structure, a build or a multiblock in a popup window.
        net.ixdarklord.folios.internal.client.page.ScenePopups.register();
        FoliosPageElements.register(Folios.rl("quest"), QuestPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("empty"), EmptyPage.CODEC);
        FoliosPageElements.register(Folios.rl("spread"), net.ixdarklord.folios.internal.client.page.SpreadPage.CODEC);
        FoliosStyles.register(FoliosStyles.SCREEN, (book, at, hand) -> {
            FoliosSession session = FoliosLibrary.session(book.id());
            if (session != null) HandheldBook.openScreen(book, session, at, hand);
        });
        FoliosStyles.register(FoliosStyles.HANDHELD, (book, at, hand) -> {
            FoliosSession session = FoliosLibrary.session(book.id());
            if (session != null) HandheldBook.open(book, session, at, hand);
        });
        ItemModelsAccessor.folios$idMapper().put(FoliosBookItemModel.TYPE, FoliosBookItemModel.Unbaked.MAP_CODEC);
        net.ixdarklord.folios.internal.mixin.client.ItemTintSourcesAccessor.folios$idMapper().put(FoliosCoverTint.TYPE, FoliosCoverTint.MAP_CODEC);
        net.ixdarklord.folios.internal.mixin.client.SpecialModelRenderersAccessor.folios$idMapper().put(FoliosCoverText.TYPE, FoliosCoverText.Unbaked.MAP_CODEC);
        FoliosHooks.opener = (book, hand) -> open(book, null, hand);
        // A book's data changing in the hand (marked as being read, and no longer) doesn't swap it out and back: it is
        // the same book while it names the same one.
        net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps.quiet(net.ixdarklord.folios.internal.core.FoliosRegistry.READING);
        net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps.register((shown, next) -> shown.getItem() instanceof net.ixdarklord.folios.api.FoliosBookItem
                && next.is(shown.getItem()) && java.util.Objects.equals(net.ixdarklord.folios.api.FoliosBooks.bookOf(shown), net.ixdarklord.folios.api.FoliosBooks.bookOf(next)));
        FoliosHooks.names = id -> {
            FoliosBook book = FoliosLibrary.book(id);
            return book != null ? book.title() : FoliosHooks.defaultName(id);
        };
        FoliosHooks.credits = FoliosLibrary::credit;
        ClientTickEvents.END.register(ObtainedItems::tick);
        ClientTickEvents.END.register(minecraft -> {
            if (pending != null) {
                Runnable run = pending;
                pending = null;
                run.run();
            }
            FoliosRecipes.tick();
            FoliosStructures.tick();
            // The books' clock counts out a pause; what moves by ticks stands still in one.
            FoliosClock.millis();
            if (!FoliosClock.paused()) net.ixdarklord.folios.internal.client.page.PreviewEntities.tick();
            net.ixdarklord.folios.internal.client.build.BuildGuide.tick(minecraft);
            if (minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
                Celebrations.check();
            }
            if (!FoliosClock.paused()) HandheldBook.tick();
            ClientQuests.tick(minecraft);
            net.ixdarklord.folios.internal.client.handheld.SharedBooks.tick();
        });
        // What the player has done with books comes from the server: a sync (on joining, and after each change) is taken
        // up by the memories without unsent changes of their own.
        net.ixdarklord.folios.internal.core.FoliosRegistry.PLAYER_PROGRESS.onChange((holder, old, now) -> {
            if (holder.isClientSide() && holder.owner() == Minecraft.getInstance().player) FoliosMemory.synced(now);
        });
        // The server's quests: books laid out again when one opens or is done, not as each step of it moves.
        net.ixdarklord.folios.internal.core.FoliosRegistry.PLAYER_QUESTS.onChange((holder, old, now) -> {
            if (holder.isClientSide() && holder.owner() == Minecraft.getInstance().player) ClientQuests.synced(old, now);
        });
        ClientPlayerEvents.LEAVE.register(player -> {
            FoliosMemory.forget();
            FoliosRecipes.clear();
            FoliosStructures.clear();
            FoliosProgress.reset();
            net.ixdarklord.folios.internal.FoliosSourceData.forget();
            net.ixdarklord.folios.internal.client.ClientQuests.forget();
            HandheldBook.drop();
            net.ixdarklord.folios.internal.client.handheld.SharedBooks.clear();
            net.ixdarklord.folios.internal.client.build.BuildGuide.stop();
        });
        // A carried build guide takes the use that sets it down, before the item or block in reach does anything.
        net.ixdarklord.coolcatcore.api.event.v2.common.InteractionEvents.USE_ITEM.register((player, hand) ->
                player.level().isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                        && net.ixdarklord.folios.internal.client.build.BuildGuide.onUse(player.isShiftKeyDown())
                        ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
        net.ixdarklord.coolcatcore.api.event.v2.common.InteractionEvents.USE_BLOCK.register((player, hand, hit) ->
                player.level().isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND
                        && net.ixdarklord.folios.internal.client.build.BuildGuide.onUse(player.isShiftKeyDown())
                        ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
        net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents.RENDER_HUD.register((graphics, delta) ->
                net.ixdarklord.folios.internal.client.build.BuildGuide.hud(graphics));
        // The key that mentions the page under the pointer in chat; what mentions a message has, shown as pages.
        net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry.register(FoliosMentions.KEY);
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("folios")
                        // What pressing a page's mention in chat runs: the book opened there.
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("mention")
                                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("page", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String page = StringArgumentType.getString(ctx, "page");
                                            pending = () -> {
                                                if (!FoliosMentions.open(page)) {
                                                    ClientCommandEvents.sendError(Component.translatableWithFallback("folios.mention.closed", "You can't open that page"));
                                                }
                                            };
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("guide")
                                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("book", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FoliosLibrary.books().keySet().stream().map(Identifier::toString), builder))
                                        .executes(ctx -> {
                                            Identifier id = Identifier.tryParse(StringArgumentType.getString(ctx, "book"));
                                            // A command's screen would be closed by the chat closing: open it a moment later.
                                            if (id == null || FoliosLibrary.book(id) == null) {
                                                ClientCommandEvents.sendError(Component.literal("No guide book with that id"));
                                                return 0;
                                            }
                                            pending = () -> open(id, null, null);
                                            return 1;
                                        })))));
    }

    /** Opens a book in its style: at an entry (with an optional "#marker"), or where the player left off for null. */
    public static boolean open(Identifier id, @Nullable String entry, @Nullable InteractionHand hand) {
        FoliosBook book = FoliosLibrary.book(id);
        if (book == null) {
            Folios.LOGGER.warn("There is no guide book {}", id);
            return false;
        }
        FoliosBook.Location at = null;
        if (entry != null) {
            FoliosSession session = FoliosLibrary.session(id);
            FoliosSession.Target target = session != null ? session.resolve(entry) : null;
            if (target == null || target.entry() == null) return false;
            at = session.location(target.entry(), target.page());
        }
        return open(book, at, hand);
    }

    /** Opens a book in its style at a page of an entry's JSON ({@code null} for where the player left off). */
    public static boolean open(Identifier id, FoliosBook.@Nullable Location at) {
        FoliosBook book = FoliosLibrary.book(id);
        return book != null && open(book, at, null);
    }

    private static boolean open(FoliosBook book, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand) {
        Identifier id = book.id();
        FoliosStyles.Style style = FoliosStyles.get(book.style());
        if (style == null) {
            Folios.LOGGER.warn("The guide book {} has the style {}, which nothing provides; showing it as a screen", id, book.style());
            style = FoliosStyles.get(FoliosStyles.SCREEN);
        }
        if (style == null) return false;
        style.open(book, at, hand);
        return true;
    }
}
