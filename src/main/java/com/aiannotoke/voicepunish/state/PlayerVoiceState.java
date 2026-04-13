package com.aiannotoke.voicepunish.state;

import com.aiannotoke.voicepunish.config.VoicePunishConfig;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.ArrayDeque;
import java.util.Deque;

public class PlayerVoiceState {

    private final Deque<FrameVolume> rollingFrames = new ArrayDeque<>();
    private final Deque<Long> loudPunishmentTimes = new ArrayDeque<>();
    private final Deque<Long> badWordPunishmentTimes = new ArrayDeque<>();

    private OpusDecoder decoder;

    private boolean selfTextEnabled = true;
    private boolean voicechatConnected;
    private boolean voicechatDisabled;
    private boolean voicechatWarned;
    private boolean transcriptWarned;
    private boolean hasEverConnectedVoicechat;

    private long joinedAtMs = -1L;
    private long disconnectedSinceMs = -1L;
    private long lastTranscriptAtMs = -1L;
    private long lastLoudPunishmentAtMs = Long.MIN_VALUE;
    private long lastTranscriptHashAtMs = -1L;
    private long lastHudVolumeSyncAtMs = Long.MIN_VALUE;
    private long lastVolumeSampleAtMs = -1L;
    private long lastClientLevelAtMs = -1L;

    private String lastTranscriptHash = "";

    private double rollingWeightedRms;
    private long rollingDurationMs;
    private double baselineWeightedRms;
    private long baselineDurationMs;
    private long overThresholdMs;
    private double latestRollingRms;
    private double latestThreshold;

    public synchronized void markJoined(long now) {
        joinedAtMs = now;
        voicechatConnected = false;
        voicechatDisabled = false;
        voicechatWarned = false;
        transcriptWarned = false;
        hasEverConnectedVoicechat = false;
        disconnectedSinceMs = now;
        lastTranscriptAtMs = -1L;
        lastClientLevelAtMs = -1L;
    }

    public synchronized void markVoicechatConnected(long now) {
        voicechatConnected = true;
        voicechatDisabled = false;
        hasEverConnectedVoicechat = true;
        disconnectedSinceMs = -1L;
        voicechatWarned = false;
    }

    public synchronized void markVoicechatDisconnected(long now, boolean disabled) {
        voicechatConnected = false;
        voicechatDisabled = disabled;
        if (disconnectedSinceMs < 0L) {
            disconnectedSinceMs = now;
        }
    }

    public synchronized void markTranscriptReceived(long now) {
        lastTranscriptAtMs = now;
        transcriptWarned = false;
    }

    public synchronized boolean shouldHandleTranscript(String normalizedText, long now, long duplicateWindowMs) {
        if (normalizedText.equals(lastTranscriptHash) && now - lastTranscriptHashAtMs <= duplicateWindowMs) {
            return false;
        }
        lastTranscriptHash = normalizedText;
        lastTranscriptHashAtMs = now;
        return true;
    }

    public synchronized LoudDecision recordAudioFrame(byte[] opusData, VoicechatApi api, long now, VoicePunishConfig config) {
        if (decoder == null || decoder.isClosed()) {
            decoder = api.createDecoder();
        }

        short[] pcm = decoder.decode(opusData);
        if (pcm == null || pcm.length == 0) {
            return new LoudDecision(false, 0D, getAdaptiveThreshold(config), getBaselineRms());
        }

        double rms = calculateRms(pcm);
        long frameDurationMs = Math.max(1L, Math.round((pcm.length / 48_000D) * 1000D));
        return recordLevel(rms, frameDurationMs, now, config);
    }

    public synchronized LoudDecision recordClientLevel(float level, long now, VoicePunishConfig config) {
        long frameDurationMs = 100L;
        if (lastVolumeSampleAtMs > 0L) {
            frameDurationMs = Math.max(25L, Math.min(250L, now - lastVolumeSampleAtMs));
        }
        lastClientLevelAtMs = now;
        return recordLevel(level, frameDurationMs, now, config);
    }

    public synchronized boolean shouldUseServerAudioFallback(long now, long freshnessMs) {
        return lastClientLevelAtMs <= 0L || now - lastClientLevelAtMs > freshnessMs;
    }

    private LoudDecision recordLevel(double level, long frameDurationMs, long now, VoicePunishConfig config) {
        level = Math.max(0D, Math.min(1D, level));
        lastVolumeSampleAtMs = now;

        double baselineSampleCeiling = Math.max(config.baselineNoiseFloor * 4D, config.minimumVolumeThreshold * 0.75D);
        if (level >= config.baselineNoiseFloor
                && level <= baselineSampleCeiling
                && baselineDurationMs < config.baselineWindowMs) {
            long addDuration = Math.min(frameDurationMs, config.baselineWindowMs - baselineDurationMs);
            baselineWeightedRms += level * addDuration;
            baselineDurationMs += addDuration;
        }

        rollingFrames.addLast(new FrameVolume(level, frameDurationMs));
        rollingWeightedRms += level * frameDurationMs;
        rollingDurationMs += frameDurationMs;

        while (rollingDurationMs > config.rollingWindowMs && !rollingFrames.isEmpty()) {
            FrameVolume frame = rollingFrames.removeFirst();
            rollingWeightedRms -= frame.rms() * frame.durationMs();
            rollingDurationMs -= frame.durationMs();
        }

        double rollingRms = rollingDurationMs > 0L ? rollingWeightedRms / rollingDurationMs : level;
        double threshold = getAdaptiveThreshold(config);
        latestRollingRms = rollingRms;
        latestThreshold = threshold;

        if (rollingRms > threshold) {
            overThresholdMs += frameDurationMs;
        } else {
            overThresholdMs = 0L;
        }

        if (canPunishForLoud(now, config.loudCooldownMs) && overThresholdMs >= config.overThresholdHoldMs) {
            lastLoudPunishmentAtMs = now;
            overThresholdMs = 0L;
            return new LoudDecision(true, rollingRms, threshold, getBaselineRms());
        }

        return new LoudDecision(false, rollingRms, threshold, getBaselineRms());
    }

    public synchronized boolean shouldWarnForVoicechat(long now, VoicePunishConfig config) {
        if (voicechatWarned) {
            return false;
        }
        if (!hasEverConnectedVoicechat) {
            return joinedAtMs > 0L && now - joinedAtMs >= config.joinVoicechatGraceMs / 2L;
        }
        return !voicechatConnected && disconnectedSinceMs > 0L && now - disconnectedSinceMs >= config.disconnectedVoicechatGraceMs / 2L;
    }

    public synchronized boolean shouldKickForVoicechat(long now, VoicePunishConfig config) {
        if (!hasEverConnectedVoicechat) {
            return joinedAtMs > 0L && now - joinedAtMs >= config.joinVoicechatGraceMs;
        }
        return !voicechatConnected && disconnectedSinceMs > 0L && now - disconnectedSinceMs >= config.disconnectedVoicechatGraceMs;
    }

    public synchronized void markVoicechatWarned() {
        voicechatWarned = true;
    }

    public synchronized void markTranscriptWarned() {
        transcriptWarned = true;
    }

    public synchronized boolean isSelfTextEnabled() {
        return selfTextEnabled;
    }

    public synchronized void setSelfTextEnabled(boolean selfTextEnabled) {
        this.selfTextEnabled = selfTextEnabled;
    }

    public synchronized double getBaselineRms() {
        return baselineDurationMs > 0L ? baselineWeightedRms / baselineDurationMs : 0.0D;
    }

    public synchronized int countRecentLoud(long now, long windowMs) {
        pruneOld(loudPunishmentTimes, now, windowMs);
        return loudPunishmentTimes.size();
    }

    public synchronized void recordLoudPunishment(long now, long windowMs) {
        pruneOld(loudPunishmentTimes, now, windowMs);
        loudPunishmentTimes.addLast(now);
    }

    public synchronized int countRecentBadWords(long now, long windowMs) {
        pruneOld(badWordPunishmentTimes, now, windowMs);
        return badWordPunishmentTimes.size();
    }

    public synchronized void recordBadWordPunishment(long now, long windowMs) {
        pruneOld(badWordPunishmentTimes, now, windowMs);
        badWordPunishmentTimes.addLast(now);
    }

    public synchronized void clearPunishments() {
        loudPunishmentTimes.clear();
        badWordPunishmentTimes.clear();
        lastTranscriptHash = "";
        lastTranscriptHashAtMs = -1L;
    }

    public synchronized void close() {
        if (decoder != null && !decoder.isClosed()) {
            decoder.close();
        }
        decoder = null;
        rollingFrames.clear();
        loudPunishmentTimes.clear();
        badWordPunishmentTimes.clear();
    }

    public synchronized boolean shouldSyncHudVolume(long now, long intervalMs) {
        if (now - lastHudVolumeSyncAtMs >= intervalMs) {
            lastHudVolumeSyncAtMs = now;
            return true;
        }
        return false;
    }

    public synchronized float getLatestVolumePercent() {
        if (latestThreshold <= 0D) {
            return 0F;
        }
        return (float) ((latestRollingRms / latestThreshold) * 100D);
    }

    public synchronized boolean isLatestThresholdExceeded() {
        return latestRollingRms > latestThreshold;
    }

    public synchronized String describe(String playerName, long now, VoicePunishConfig config) {
        return "Voice Punish state [" + playerName + "] "
                + "voicechatConnected=" + voicechatConnected
                + ", voicechatDisabled=" + voicechatDisabled
                + ", selfTextEnabled=" + selfTextEnabled
                + ", baselineRms=" + String.format("%.3f", getBaselineRms())
                + ", threshold=" + String.format("%.3f", getAdaptiveThreshold(config))
                + ", latestRolling=" + String.format("%.3f", latestRollingRms)
                + ", recentLoud=" + countRecentLoud(now, config.loudEscalateWindowMs)
                + ", recentBadword=" + countRecentBadWords(now, config.badWordEscalateWindowMs)
                + ", lastTranscriptAt=" + lastTranscriptAtMs
                + ", lastClientLevelAt=" + lastClientLevelAtMs;
    }

    private double getAdaptiveThreshold(VoicePunishConfig config) {
        return Math.max(0.01D, config.minimumVolumeThreshold);
    }

    private boolean canPunishForLoud(long now, long cooldownMs) {
        return lastLoudPunishmentAtMs <= Long.MIN_VALUE || now - lastLoudPunishmentAtMs >= cooldownMs;
    }

    private void pruneOld(Deque<Long> times, long now, long windowMs) {
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
            times.removeFirst();
        }
    }

    private double calculateRms(short[] pcm) {
        double squareSum = 0D;
        for (short sample : pcm) {
            double normalized = sample / 32768D;
            squareSum += normalized * normalized;
        }
        return Math.sqrt(squareSum / pcm.length);
    }

    private record FrameVolume(double rms, long durationMs) {
    }

    public record LoudDecision(boolean shouldPunish, double rollingRms, double threshold, double baselineRms) {
    }
}
