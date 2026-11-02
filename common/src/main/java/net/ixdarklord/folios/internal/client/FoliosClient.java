package net.ixdarklord.folios.internal.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.coolcatcore.api.client.item.HeldItemSwaps;
import net.ixdarklord.coolcatcore.api.client.registry.KeyMappingRegistry;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientCommandEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientGuiEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientPlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.InteractionEvents;
import net.ixdarklord.folios.api.FoliosBookItem;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosStyles;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.internal.FoliosHooks;
import net.ixdarklord.folios.internal.FoliosSourceData;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.ixdarklord.folios.internal.client.handheld.SharedBooks;
import net.ixdarklord.folios.internal.client.page.BuildPageElement;
import net.ixdarklord.folios.internal.client.page.EmptyPage;
import net.ixdarklord.folios.internal.client.page.EntityPageElement;
import net.ixdarklord.folios.internal.client.page.ImagePageElement;
import net.ixdarklord.folios.internal.client.page.LinksPageElement;
import net.ixdarklord.folios.internal.client.page.MultiblockPageElement;
import net.ixdarklord.folios.internal.client.page.PreviewEntities;
import net.ixdarklord.folios.internal.client.page.QuestPageElement;
import net.ixdarklord.folios.internal.client.page.RecipePageElement;
import net.ixdarklord.folios.internal.client.page.ScenePopups;
import net.ixdarklord.folios.internal.client.page.SpotlightPageElement;
import net.ixdarklord.folios.internal.client.page.SpreadPage;
import net.ixdarklord.folios.internal.client.page.StructurePageElement;
import net.ixdarklord.folios.internal.client.page.TextPage;
import net.ixdarklord.folios.internal.client.screen.FoliosScreen;
import net.ixdarklord.folios.internal.config.FoliosClientConfig;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.ixdarklord.folios.internal.mixin.client.ItemModelsAccessor;
import net.ixdarklord.folios.internal.mixin.client.ItemTintSourcesAccessor;
import net.ixdarklord.folios.internal.mixin.client.SpecialModelRenderersAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class FoliosClient {
    private static @Nullable Runnable pending;

    private FoliosClient() {}

    public static void init() {
        FoliosClientConfig.CONFIG.id();
        FoliosPageElements.register(Folios.rl("text"), TextPage.CODEC);
        FoliosPageElements.register(Folios.rl("spotlight"), SpotlightPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("image"), ImagePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("recipe"), RecipePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("entity"), EntityPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("multiblock"), MultiblockPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("structure"), StructurePageElement.CODEC);
        FoliosPageElements.register(Folios.rl("build"), BuildPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("links"), LinksPageElement.CODEC);
        ScenePopups.register();
        FoliosPageElements.register(Folios.rl("quest"), QuestPageElement.CODEC);
        FoliosPageElements.register(Folios.rl("empty"), EmptyPage.CODEC);
        FoliosPageElements.register(Folios.rl("spread"), SpreadPage.CODEC);
        FoliosStyles.register(FoliosStyles.SCREEN, (book, at, hand) -> {
            FoliosSession session = FoliosLibrary.session(book.id());
            if (session != null) HandheldBook.openScreen(book, session, at, hand);
        });
        FoliosStyles.register(FoliosStyles.HANDHELD, (book, at, hand) -> {
            FoliosSession session = FoliosLibrary.session(book.id());
            if (session != null) HandheldBook.open(book, session, at, hand);
        });
        ItemModelsAccessor.folios$idMapper().put(FoliosBookItemModel.TYPE, FoliosBookItemModel.Unbaked.MAP_CODEC);
        ItemTintSourcesAccessor.folios$idMapper().put(FoliosCoverTint.TYPE, FoliosCoverTint.MAP_CODEC);
        SpecialModelRenderersAccessor.folios$idMapper().put(FoliosCoverText.TYPE, FoliosCoverText.Unbaked.MAP_CODEC);
        FoliosHooks.opener = (book, hand) -> open(book, null, hand);
        HeldItemSwaps.quiet(FoliosRegistry.READING);
        HeldItemSwaps.register((shown, next) -> shown.getItem() instanceof FoliosBookItem
                && next.is(shown.getItem()) && Objects.equals(FoliosBooks.bookOf(shown), FoliosBooks.bookOf(next)));
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
            FoliosClock.millis();
            if (!FoliosClock.paused()) PreviewEntities.tick();
            BuildGuide.tick(minecraft);
            if (minecraft.player != null && minecraft.player.tickCount % 20 == 0) {
                Celebrations.check();
            }
            if (!FoliosClock.paused()) HandheldBook.tick();
            ClientQuests.tick(minecraft);
            SharedBooks.tick();
        });
        FoliosRegistry.PLAYER_PROGRESS.onChange((holder, old, now) -> {
            if (holder.isClientSide() && holder.owner() == Minecraft.getInstance().player) FoliosMemory.synced(now);
        });
        FoliosRegistry.PLAYER_QUESTS.onChange((holder, old, now) -> {
            if (holder.isClientSide() && holder.owner() == Minecraft.getInstance().player) ClientQuests.synced(old, now);
        });
        ClientPlayerEvents.LEAVE.register(player -> {
            FoliosMemory.forget();
            FoliosRecipes.clear();
            FoliosStructures.clear();
            FoliosProgress.reset();
            FoliosSourceData.forget();
            ClientQuests.forget();
            HandheldBook.drop();
            SharedBooks.clear();
            BuildGuide.stop();
        });
        InteractionEvents.USE_ITEM.register((player, hand) ->
                player.level().isClientSide() && hand == InteractionHand.MAIN_HAND
                        && BuildGuide.onUse(player.isShiftKeyDown())
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        InteractionEvents.USE_BLOCK.register((player, hand, hit) ->
                player.level().isClientSide() && hand == InteractionHand.MAIN_HAND
                        && BuildGuide.onUse(player.isShiftKeyDown())
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        ClientGuiEvents.RENDER_HUD.register((graphics, delta) ->
                BuildGuide.hud(graphics));
        KeyMappingRegistry.register(FoliosMentions.KEY);
        KeyMappingRegistry.register(BookLookup.KEY);
        ClientTickEvents.END.register(BookLookup::tick);
        ClientCommandEvents.REGISTER.register((dispatcher, context) -> dispatcher.register(
                LiteralArgumentBuilder.<SharedSuggestionProvider>literal("folios")
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
                                            if (id == null || FoliosLibrary.book(id) == null) {
                                                ClientCommandEvents.sendError(Component.literal("No guide book with that id"));
                                                return 0;
                                            }
                                            pending = () -> open(id, null, null);
                                            return 1;
                                        })))));
    }

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
