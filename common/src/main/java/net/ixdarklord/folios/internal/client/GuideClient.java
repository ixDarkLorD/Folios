package net.ixdarklord.folios.internal.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideStyles;
import net.ixdarklord.folios.api.client.page.GuidePageTypes;
import net.ixdarklord.folios.internal.GuideHooks;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.ixdarklord.folios.internal.client.page.EmptyPage;
import net.ixdarklord.folios.internal.client.page.EntityPage;
import net.ixdarklord.folios.internal.client.page.ImagePage;
import net.ixdarklord.folios.internal.client.page.LinksPage;
import net.ixdarklord.folios.internal.client.page.MultiblockPage;
import net.ixdarklord.folios.internal.client.page.QuestPage;
import net.ixdarklord.folios.internal.client.page.RecipePage;
import net.ixdarklord.folios.internal.client.page.SpotlightPage;
import net.ixdarklord.folios.internal.client.page.TextPage;
import net.ixdarklord.folios.internal.client.screen.GuideScreen;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

// Sets up guide books on the client: the kinds of page and book, the item's model, and what opens a book.
public final class GuideClient {
    // A book a command asked for, opened at the next tick (the chat closing would otherwise close it).
    private static @Nullable Runnable pending;

    private GuideClient() {}

    public static void init() {
        net.ixdarklord.folios.internal.config.FoliosClientConfig.CONFIG.id();
        GuidePageTypes.register(Folios.rl("text"), TextPage.CODEC);
        GuidePageTypes.register(Folios.rl("spotlight"), SpotlightPage.CODEC);
        GuidePageTypes.register(Folios.rl("image"), ImagePage.CODEC);
        GuidePageTypes.register(Folios.rl("recipe"), RecipePage.CODEC);
        GuidePageTypes.register(Folios.rl("entity"), EntityPage.CODEC);
        GuidePageTypes.register(Folios.rl("multiblock"), MultiblockPage.CODEC);
        GuidePageTypes.register(Folios.rl("links"), LinksPage.CODEC);
        GuidePageTypes.register(Folios.rl("quest"), QuestPage.CODEC);
        GuidePageTypes.register(Folios.rl("empty"), EmptyPage.CODEC);
        GuidePageTypes.register(Folios.rl("spread"), net.ixdarklord.folios.internal.client.page.SpreadPage.CODEC);
        GuideStyles.register(GuideStyles.SCREEN, (book, at, hand) -> {
            GuideSession session = GuideLibrary.session(book.id());
            if (session != null) HandheldBook.openScreen(book, session, at, hand);
        });
        GuideStyles.register(GuideStyles.HANDHELD, (book, at, hand) -> {
            GuideSession session = GuideLibrary.session(book.id());
            if (session != null) HandheldBook.open(book, session, at, hand);
        });
        // The book item is drawn in code, from models baked with the game's.
        GuideBookItemModel.registerModels();
        net.ixdarklord.coolcatcore.api.client.registry.ItemRendererRegistry.register(net.ixdarklord.folios.internal.core.GuideRegistry.BOOK_ITEM, GuideBookItemModel::render);
        GuideHooks.opener = (book, hand) -> open(book, null, hand);
        // A book's data changing in the hand (marked as being read, and no longer) doesn't swap it out and back: it is
        // the same book while it names the same one.
        net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps.quiet(net.ixdarklord.folios.internal.core.GuideRegistry.READING);
        net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps.register((shown, next) -> shown.getItem() instanceof net.ixdarklord.folios.api.GuideBookItem
                && next.is(shown.getItem()) && java.util.Objects.equals(net.ixdarklord.folios.api.GuideBooks.bookOf(shown), net.ixdarklord.folios.api.GuideBooks.bookOf(next)));
        GuideHooks.names = id -> {
            GuideBook book = GuideLibrary.book(id);
            return book != null ? book.title() : GuideHooks.defaultName(id);
        };
        ClientTickEvents.END.register(minecraft -> {
            if (pending != null) {
                Runnable run = pending;
                pending = null;
                run.run();
            }
            GuideRecipes.tick();
            net.ixdarklord.folios.internal.client.build.BuildGuide.tick(minecraft);
            if (minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
                QuestToasts.check();
                Celebrations.check();
            }
            HandheldBook.tick();
            net.ixdarklord.folios.internal.client.handheld.SharedBooks.tick();
        });
        // What the player has done with books comes from the server: a sync (on joining, and after each change) is taken
        // up by the memories without unsent changes of their own.
        net.ixdarklord.folios.internal.core.GuideRegistry.PLAYER_PROGRESS.onChange((holder, old, now) -> {
            if (holder.isClientSide() && holder.owner() == Minecraft.getInstance().player) GuideMemory.synced(now);
        });
        ClientPlayerEvents.LEAVE.register(player -> {
            GuideMemory.forget();
            GuideRecipes.clear();
            GuideProgress.reset();
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
                net.ixdarklord.folios.internal.client.build.BuildGuide.hud(net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor.of(graphics)));
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("folios")
                        .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("guide")
                                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("book", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(GuideLibrary.books().keySet().stream().map(ResourceLocation::toString), builder))
                                        .executes(ctx -> {
                                            ResourceLocation id = ResourceLocation.tryParse(StringArgumentType.getString(ctx, "book"));
                                            // A command's screen would be closed by the chat closing: open it a moment later.
                                            if (id == null || GuideLibrary.book(id) == null) {
                                                ClientCommandEvents.sendError(Component.literal("No guide book with that id"));
                                                return 0;
                                            }
                                            pending = () -> open(id, null, null);
                                            return 1;
                                        })))));
    }

    /** Opens a book in its style: at an entry (with an optional "#anchor"), or where the player left off for null. */
    public static boolean open(ResourceLocation id, @Nullable String entry, @Nullable InteractionHand hand) {
        GuideBook book = GuideLibrary.book(id);
        if (book == null) {
            Folios.LOGGER.warn("There is no guide book {}", id);
            return false;
        }
        GuideBook.Location at = null;
        if (entry != null) {
            GuideSession session = GuideLibrary.session(id);
            GuideSession.Target target = session != null ? session.resolve(entry) : null;
            if (target == null || target.entry() == null) return false;
            at = session.location(target.entry(), target.page());
        }
        GuideStyles.Style style = GuideStyles.get(book.style());
        if (style == null) {
            Folios.LOGGER.warn("The guide book {} has the style {}, which nothing provides; showing it as a screen", id, book.style());
            style = GuideStyles.get(GuideStyles.SCREEN);
        }
        if (style == null) return false;
        style.open(book, at, hand);
        return true;
    }
}
