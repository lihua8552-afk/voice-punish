package com.aiannotoke.voicepunish.command;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.moderation.TranscriptMatchResult;
import com.aiannotoke.voicepunish.punishment.PunishmentCause;
import com.aiannotoke.voicepunish.service.VoiceModerationService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class VoicePunishCommands {

    private VoicePunishCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("voicepunish")
                .then(CommandManager.literal("panel")
                        .requires(VoicePunishCommands::isGamemaster)
                        .executes(context -> openPanel(context.getSource())))
                .then(CommandManager.literal("editor")
                        .requires(VoicePunishCommands::isGamemaster)
                        .executes(context -> openPanel(context.getSource())))
                .then(CommandManager.literal("reload")
                        .requires(VoicePunishCommands::isGamemaster)
                        .executes(context -> {
                            VoicePunishMod.getService().reloadConfig();
                            context.getSource().sendFeedback(() -> Text.literal("Voice Punish config reloaded"), false);
                            return 1;
                        }))
                .then(CommandManager.literal("selftext")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setSelfText(context, null))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> setSelfText(context, EntityArgumentType.getPlayer(context, "player"))))))
                .then(CommandManager.literal("status")
                        .requires(VoicePunishCommands::isGamemaster)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    VoicePunishMod.getService().sendStatus(context.getSource(), player);
                                    return 1;
                                })))
                .then(CommandManager.literal("pardon")
                        .requires(VoicePunishCommands::isGamemaster)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                    VoicePunishMod.getService().pardonPlayer(player);
                                    context.getSource().sendFeedback(() -> Text.literal("Cleared punishment history for " + player.getGameProfile().name()), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("test")
                        .requires(VoicePunishCommands::isGamemaster)
                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("loud");
                                    builder.suggest("badword");
                                    builder.suggest("event");
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeTest(context, null))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> executeTest(context, EntityArgumentType.getPlayer(context, "player")))))));
    }

    private static int setSelfText(CommandContext<ServerCommandSource> context, ServerPlayerEntity explicitTarget) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ServerPlayerEntity target = explicitTarget;

        if (target == null) {
            try {
                target = context.getSource().getPlayerOrThrow();
            } catch (Exception ignored) {
                context.getSource().sendError(Text.literal("Console must specify a player"));
                return 0;
            }
        }

        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
        if (!isGamemaster(context.getSource()) && (sourcePlayer == null || !sourcePlayer.getUuid().equals(target.getUuid()))) {
            context.getSource().sendError(Text.literal("You can only change your own transcript HUD toggle"));
            return 0;
        }

        VoicePunishMod.getService().setSelfTextEnabled(target, enabled);
        String playerName = target.getGameProfile().name();
        context.getSource().sendFeedback(() -> Text.literal(playerName + " transcript HUD is now " + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static int executeTest(CommandContext<ServerCommandSource> context, ServerPlayerEntity explicitTarget) {
        VoiceModerationService service = VoicePunishMod.getService();
        ServerPlayerEntity target = explicitTarget;

        if (target == null) {
            try {
                target = context.getSource().getPlayerOrThrow();
            } catch (Exception ignored) {
                context.getSource().sendError(Text.literal("Console must specify a player"));
                return 0;
            }
        }

        String mode = StringArgumentType.getString(context, "mode");
        switch (mode) {
            case "loud" -> service.applySyntheticPunishment(target, PunishmentCause.LOUD, null, "Admin test: loud voice");
            case "badword" -> service.applySyntheticPunishment(
                    target,
                    PunishmentCause.BAD_WORD,
                    TranscriptMatchResult.synthetic("test bad word message", "testbadwordmessage", "bad word"),
                    "Admin test: bad words"
            );
            case "event" -> service.applySyntheticPunishment(target, PunishmentCause.TEST_EVENT, null, "Admin test: random event");
            default -> {
                context.getSource().sendError(Text.literal("Unknown test mode: " + mode));
                return 0;
            }
        }

        String playerName = target.getGameProfile().name();
        context.getSource().sendFeedback(() -> Text.literal("Ran test mode '" + mode + "' for " + playerName), false);
        return 1;
    }

    private static boolean isGamemaster(ServerCommandSource source) {
        return CommandManager.GAMEMASTERS_CHECK.allows(source.getPermissions());
    }

    private static int openPanel(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            VoicePunishMod.getService().openConfigEditor(player);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("The settings panel can only be opened by an in-game OP player"));
            return 0;
        }
    }
}
