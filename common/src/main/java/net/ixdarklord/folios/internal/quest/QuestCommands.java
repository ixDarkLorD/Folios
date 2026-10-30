package net.ixdarklord.folios.internal.quest;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.ixdarklord.coolcatcore.api.event.v2.common.CommandEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

final class QuestCommands {
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestTracker.quests(), builder);

    private QuestCommands() {}

    static void init() {
        CommandEvents.REGISTER.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("folios")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(QuestCommands::list))
                        .then(onQuest("status", QuestCommands::status))
                        .then(onQuest("complete", QuestCommands::complete))
                        .then(onQuest("reset", QuestCommands::reset))
                        .then(Commands.literal("flag").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("flag", StringArgumentType.string())
                                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(QuestCommands::flag))))))));
    }

    @FunctionalInterface
    private interface Action {
        int run(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ResourceLocation quest) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> onQuest(String name, Action action) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .executes(context -> action.run(context, EntityArgument.getPlayers(context, "players"), ResourceLocationArgument.getId(context, "quest")))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var quests = QuestTracker.quests();
        context.getSource().sendSuccess(() -> Component.literal(quests.size() + " Folios quests: " + String.join(", ", quests.stream().map(ResourceLocation::toString).toList())), false);
        return quests.size();
    }

    private static int status(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ResourceLocation quest) {
        for (ServerPlayer player : players) {
            PlayerQuests.Progress progress = QuestTracker.state(player).progress(quest);
            int percent = Math.round(QuestTracker.progress(player, quest) * 100);
            String state = progress.claimed() ? "done, reward claimed" : progress.done() ? "done" : percent + "% along";
            context.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + ": " + quest + " is " + state), false);
        }
        return players.size();
    }

    private static int complete(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ResourceLocation quest) {
        for (ServerPlayer player : players) QuestTracker.complete(player, quest);
        context.getSource().sendSuccess(() -> Component.literal("Finished " + quest + " for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int reset(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, ResourceLocation quest) {
        for (ServerPlayer player : players) QuestTracker.reset(player, quest);
        context.getSource().sendSuccess(() -> Component.literal("Took back " + quest + " for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int flag(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        String flag = StringArgumentType.getString(context, "flag");
        boolean on = BoolArgumentType.getBool(context, "on");
        for (ServerPlayer player : players) QuestTracker.setFlag(player, flag, on);
        context.getSource().sendSuccess(() -> Component.literal((on ? "Set " : "Cleared ") + flag + " for " + players.size() + " player(s)"), true);
        return players.size();
    }
}
