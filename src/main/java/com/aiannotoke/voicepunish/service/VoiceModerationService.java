package com.aiannotoke.voicepunish.service;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.ConfigManager;
import com.aiannotoke.voicepunish.config.VoicePunishConfig;
import com.aiannotoke.voicepunish.config.VoicePunishSettingsSnapshot;
import com.aiannotoke.voicepunish.moderation.TranscriptEnhancer;
import com.aiannotoke.voicepunish.moderation.TextNormalizer;
import com.aiannotoke.voicepunish.moderation.TranscriptMatchResult;
import com.aiannotoke.voicepunish.network.OpenConfigEditorPayload;
import com.aiannotoke.voicepunish.network.VoiceHudPayload;
import com.aiannotoke.voicepunish.network.VoiceRecognitionHintsPayload;
import com.aiannotoke.voicepunish.punishment.PunishmentCause;
import com.aiannotoke.voicepunish.punishment.PunishmentRoll;
import com.aiannotoke.voicepunish.state.PlayerVoiceState;
import com.aiannotoke.voicepunish.util.TextRepairUtil;
import de.maxhenkel.voicechat.api.VoicechatApi;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceModerationService {

    private static final long CLIENT_LEVEL_FALLBACK_GRACE_MS = 1000L;

    private final ConfigManager configManager;
    private final PunishmentEngine punishmentEngine;
    private final Map<UUID, PlayerVoiceState> playerStates;

    private volatile VoicePunishConfig config;
    private volatile VoicechatApi voicechatApi;
    private volatile MinecraftServer server;

    public VoiceModerationService(ConfigManager configManager) {
        this.configManager = configManager;
        this.config = configManager.loadOrCreate();
        this.punishmentEngine = new PunishmentEngine();
        this.playerStates = new ConcurrentHashMap<>();
    }

    public void setVoicechatApi(VoicechatApi voicechatApi) {
        this.voicechatApi = voicechatApi;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public boolean shouldMuteIncomingVoicePlayback() {
        return config.muteIncomingVoicePlayback;
    }

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
    }

    public void onServerStopping(MinecraftServer server) {
        playerStates.values().forEach(PlayerVoiceState::close);
        playerStates.clear();
        this.server = null;
    }

    public void onEndServerTick(MinecraftServer server) {
        // Voice chat presence is no longer enforced here.
    }

    public void onPlayerJoined(ServerPlayerEntity player) {
        getOrCreateState(player).markJoined(System.currentTimeMillis());
        sendRecognitionHints(player);
    }

    public void onPlayerLeft(ServerPlayerEntity player) {
        PlayerVoiceState state = playerStates.remove(player.getUuid());
        if (state != null) {
            state.close();
        }
    }

    public void onVoicechatConnected(ServerPlayerEntity player) {
        getOrCreateState(player).markVoicechatConnected(System.currentTimeMillis());
    }

    public void onVoicechatDisconnected(UUID uuid, boolean disabled) {
        playerStates.computeIfAbsent(uuid, ignored -> new PlayerVoiceState()).markVoicechatDisconnected(System.currentTimeMillis(), disabled);
    }

    public void onVoicechatStateChanged(UUID uuid, boolean disconnected, boolean disabled) {
        if (!disconnected && !disabled && server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                onVoicechatConnected(player);
                return;
            }
        }

        if (disconnected || disabled) {
            onVoicechatDisconnected(uuid, disabled);
        }
    }

    public void onMicrophonePacket(ServerPlayerEntity player, byte[] opusData, boolean whispering, long now) {
        PlayerVoiceState state = getOrCreateState(player);
        state.markVoicechatConnected(now);

        if (whispering || !config.enableVolumeModeration || opusData == null || opusData.length == 0 || voicechatApi == null) {
            return;
        }

        if (!state.shouldUseServerAudioFallback(now, CLIENT_LEVEL_FALLBACK_GRACE_MS)) {
            return;
        }

        // Keep this as a fallback path in case the client-side sampler cannot report levels.
        state.recordAudioFrame(opusData, voicechatApi, now, config);
    }

    public void onClientVoiceLevel(ServerPlayerEntity player, float level) {
        if (!config.enableVolumeModeration) {
            return;
        }

        long now = System.currentTimeMillis();
        PlayerVoiceState state = getOrCreateState(player);
        PlayerVoiceState.LoudDecision decision = state.recordClientLevel(level, now, config);
        if (decision.shouldPunish()) {
            applyLoudPunishment(player, state, decision);
        }
    }

    public void onTranscript(ServerPlayerEntity player, String text) {
        String repairedText = TextRepairUtil.repairIfNeeded(text);
        if (repairedText != null) {
            repairedText = TranscriptEnhancer.enhanceTranscript(repairedText.trim());
        }
        if (repairedText == null || repairedText.isBlank()) {
            return;
        }

        PlayerVoiceState state = getOrCreateState(player);
        TranscriptMatchResult result = TextNormalizer.analyze(repairedText, config.badWords);
        long now = System.currentTimeMillis();
        state.markTranscriptReceived(now);

        if (config.logAllTranscriptsToConsole) {
            VoicePunishMod.LOGGER.info("[Transcript] {}: {}", player.getGameProfile().name(), repairedText);
        }

        if (state.isSelfTextEnabled()) {
            sendHudUpdate(
                    player,
                    repairedText,
                    result.highlightRanges().stream().mapToInt(TranscriptMatchResult.HighlightRange::startInclusive).toArray(),
                    result.highlightRanges().stream().mapToInt(TranscriptMatchResult.HighlightRange::endExclusive).toArray(),
                    0F,
                    false
            );
        }

        if (config.enableTranscriptModeration
                && result.hasMatches()
                && state.shouldHandleTranscript(result.normalizedText(), now, config.duplicateTranscriptWindowMs)) {
            applyBadWordPunishment(player, state, result, "检测到违规词: " + String.join(", ", result.matchedWords()));
        }
    }

    public void onClientTranscript(ServerPlayerEntity player, String text) {
        onTranscript(player, text);
    }

    public void reloadConfig() {
        this.config = configManager.reload();
        broadcastRecognitionHints();
    }

    public void setSelfTextEnabled(ServerPlayerEntity player, boolean enabled) {
        getOrCreateState(player).setSelfTextEnabled(enabled);
        player.sendMessage(Text.literal("[Voice Punish] 你的 HUD 文字显示已" + (enabled ? "开启" : "关闭")).formatted(Formatting.AQUA), false);
    }

    public void sendStatus(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerVoiceState state = getOrCreateState(player);
        source.sendFeedback(() -> Text.literal(state.describe(player.getGameProfile().name(), System.currentTimeMillis(), config)), false);
    }

    public void pardonPlayer(ServerPlayerEntity player) {
        getOrCreateState(player).clearPunishments();
        player.sendMessage(Text.literal("[Voice Punish] 你的语音处罚累计已被清除").formatted(Formatting.GREEN), false);
    }

    public void openConfigEditor(ServerPlayerEntity player) {
        if (!CommandManager.GAMEMASTERS_CHECK.allows(player.getPermissions())) {
            player.sendMessage(Text.literal("[Voice Punish] 只有 OP 可以打开设置面板").formatted(Formatting.RED), false);
            return;
        }

        if (!ServerPlayNetworking.canSend(player, OpenConfigEditorPayload.ID)) {
            player.sendMessage(Text.literal("[Voice Punish] 客户端没有准备好设置面板通信，请确认已安装最新模组").formatted(Formatting.RED), false);
            return;
        }

        ServerPlayNetworking.send(
                player,
                new OpenConfigEditorPayload(
                        VoicePunishSettingsSnapshot.fromConfig(config),
                        configManager.getConfigPath().toAbsolutePath().toString()
                )
        );
    }

    public void applyEditorSnapshot(ServerPlayerEntity player, VoicePunishSettingsSnapshot snapshot) {
        if (!CommandManager.GAMEMASTERS_CHECK.allows(player.getPermissions())) {
            player.sendMessage(Text.literal("[Voice Punish] 只有 OP 可以保存设置面板").formatted(Formatting.RED), false);
            return;
        }

        String error = snapshot.validate();
        if (error != null) {
            player.sendMessage(Text.literal("[Voice Punish] 保存失败: " + error).formatted(Formatting.RED), false);
            return;
        }

        VoicePunishConfig updated = snapshot.applyTo(config.copy());
        configManager.save(updated);
        config = updated;
        broadcastRecognitionHints();

        player.sendMessage(Text.literal("[Voice Punish] 设置已保存到 " + configManager.getConfigPath()).formatted(Formatting.GREEN), false);
        VoicePunishMod.LOGGER.info("Voice Punish settings updated by {}", player.getGameProfile().name());
    }

    public void broadcastRecognitionHints() {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendRecognitionHints(player);
        }
    }

    public void sendRecognitionHints(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, VoiceRecognitionHintsPayload.ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new VoiceRecognitionHintsPayload(buildRecognitionHints()));
    }

    public void applySyntheticPunishment(ServerPlayerEntity player, PunishmentCause cause, TranscriptMatchResult result, String reason) {
        PlayerVoiceState state = getOrCreateState(player);
        switch (cause) {
            case LOUD -> applyLoudPunishment(player, state, new PlayerVoiceState.LoudDecision(true, 0.99D, config.minimumVolumeThreshold, state.getBaselineRms()));
            case BAD_WORD -> applyBadWordPunishment(
                    player,
                    state,
                    result == null ? TranscriptMatchResult.synthetic("测试违禁词", "测试违禁词", "测试违禁词") : result,
                    reason
            );
            case TEST_EVENT -> {
                PunishmentRoll roll = punishmentEngine.apply(player, cause, 0F, 1, false, config);
                notifyPunishment(player, cause, roll, reason);
            }
        }
    }

    private void applyLoudPunishment(ServerPlayerEntity player, PlayerVoiceState state, PlayerVoiceState.LoudDecision decision) {
        long now = System.currentTimeMillis();
        boolean escalated = state.countRecentLoud(now, config.loudEscalateWindowMs) >= config.loudEscalateAfter;
        float damage = escalated ? config.loudEscalatedDamage : config.loudDamage;
        int eventCount = escalated ? config.escalatedEventCount : config.defaultEventCount;
        PunishmentRoll roll = punishmentEngine.apply(player, PunishmentCause.LOUD, damage, eventCount, escalated, config);
        state.recordLoudPunishment(now, config.loudEscalateWindowMs);
        String reason = String.format("音量过大 (%.3f > %.3f)", decision.rollingRms(), decision.threshold());
        notifyPunishment(player, PunishmentCause.LOUD, roll, reason);
    }

    private void applyBadWordPunishment(ServerPlayerEntity player, PlayerVoiceState state, TranscriptMatchResult result, String reason) {
        long now = System.currentTimeMillis();
        boolean escalated = state.countRecentBadWords(now, config.badWordEscalateWindowMs) >= config.badWordEscalateAfter;
        float damage = escalated ? config.badWordEscalatedDamage : config.badWordDamage;
        int eventCount = escalated ? config.escalatedEventCount : config.defaultEventCount;
        PunishmentRoll roll = punishmentEngine.apply(player, PunishmentCause.BAD_WORD, damage, eventCount, escalated, config);
        state.recordBadWordPunishment(now, config.badWordEscalateWindowMs);
        notifyPunishment(player, PunishmentCause.BAD_WORD, roll, reason + " | 原文: " + result.rawText());
    }

    private void notifyPunishment(ServerPlayerEntity player, PunishmentCause cause, PunishmentRoll roll, String reason) {
        MutableText message = Text.literal("[Voice Punish] ").formatted(Formatting.RED)
                .append(Text.literal(reason).formatted(Formatting.GOLD))
                .append(Text.literal(" -> 扣血 " + roll.damage() + " HP").formatted(Formatting.RED));

        if (!roll.eventSummaries().isEmpty()) {
            message.append(Text.literal(" | 事件: " + String.join(" / ", roll.eventSummaries())).formatted(Formatting.DARK_RED));
        }

        if (roll.escalated()) {
            message.append(Text.literal(" | 升级处罚").formatted(Formatting.BOLD, Formatting.DARK_PURPLE));
        }

        player.sendMessage(message, false);
        sendAuditToOps(player, message);
        VoicePunishMod.LOGGER.warn("[Punish] {} [{}] -> {}", player.getGameProfile().name(), cause, message.getString());
    }

    private void sendAuditToOps(ServerPlayerEntity target, Text message) {
        if (server == null) {
            return;
        }

        Text audit = Text.literal("[审计] " + target.getGameProfile().name() + " ").formatted(Formatting.GRAY).append(message);
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (CommandManager.GAMEMASTERS_CHECK.allows(online.getPermissions()) && online.getUuid() != target.getUuid()) {
                online.sendMessage(audit, false);
            }
        }
    }

    private void sendHudUpdate(ServerPlayerEntity player, String transcript, int[] highlightStarts, int[] highlightEnds, float volumePercent, boolean thresholdExceeded) {
        if (!ServerPlayNetworking.canSend(player, VoiceHudPayload.ID)) {
            return;
        }

        ServerPlayNetworking.send(
                player,
                new VoiceHudPayload(transcript, highlightStarts, highlightEnds, volumePercent, thresholdExceeded)
        );
    }

    private PlayerVoiceState getOrCreateState(ServerPlayerEntity player) {
        return playerStates.computeIfAbsent(player.getUuid(), ignored -> new PlayerVoiceState());
    }

    private List<String> buildRecognitionHints() {
        LinkedHashSet<String> hints = new LinkedHashSet<>(TranscriptEnhancer.recognitionHintTerms());
        for (String badWord : config.badWords) {
            hints.addAll(TranscriptEnhancer.expandRecognitionVariants(badWord));
        }
        for (String hintTerm : TranscriptEnhancer.recognitionHintTerms()) {
            hints.addAll(TranscriptEnhancer.expandRecognitionVariants(hintTerm));
        }
        return List.copyOf(hints);
    }
}
