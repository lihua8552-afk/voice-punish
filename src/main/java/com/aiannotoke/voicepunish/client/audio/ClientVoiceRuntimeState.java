package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishSettingsSnapshot;

public final class ClientVoiceRuntimeState {

    private static volatile float latestDisplayLevel;
    private static volatile float latestModerationLevel;
    private static volatile long latestSampleAt;
    private static volatile double loudThreshold = 0.18D;

    private ClientVoiceRuntimeState() {
    }

    public static void updateFromPcm(byte[] pcm) {
        if (pcm == null || pcm.length < 2) {
            return;
        }

        int samples = pcm.length / 2;
        double squareSum = 0D;
        double peak = 0D;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int low = pcm[i] & 0xFF;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            double normalized = sample / 32768D;
            squareSum += normalized * normalized;
            double abs = Math.abs(normalized);
            if (abs > peak) {
                peak = abs;
            }
        }

        float rms = (float) Math.sqrt(squareSum / samples);
        float moderationLevel = Math.min(1F, Math.max((float) (rms * 6.0F), (float) peak));
        float displayLevel = Math.min(1F, Math.max((float) (rms * 20F), (float) (peak * 4F)));
        latestModerationLevel = Math.max(moderationLevel, latestModerationLevel * 0.72F);
        latestDisplayLevel = Math.max(displayLevel, latestDisplayLevel * 0.85F);
        latestSampleAt = System.currentTimeMillis();
    }

    public static float getLatestModerationLevel() {
        long age = System.currentTimeMillis() - latestSampleAt;
        if (age > 1500L) {
            latestModerationLevel *= 0.55F;
        } else if (age > 400L) {
            latestModerationLevel *= 0.88F;
        }
        if (latestModerationLevel < 0.001F) {
            latestModerationLevel = 0F;
        }
        return latestModerationLevel;
    }

    public static float getLatestDisplayLevel() {
        long age = System.currentTimeMillis() - latestSampleAt;
        if (age > 1500L) {
            latestDisplayLevel *= 0.7F;
        } else if (age > 400L) {
            latestDisplayLevel *= 0.92F;
        }
        if (latestDisplayLevel < 0.001F) {
            latestDisplayLevel = 0F;
        }
        return latestDisplayLevel;
    }

    public static float getLatestPercent() {
        return Math.max(0F, Math.min(100F, getLatestDisplayLevel() * 100F));
    }

    public static float getLatestPercentOfThreshold() {
        double threshold = Math.max(0.01D, loudThreshold);
        return (float) ((getLatestModerationLevel() / threshold) * 100D);
    }

    public static float getLatestThresholdRatio() {
        double threshold = Math.max(0.01D, loudThreshold);
        return (float) (getLatestModerationLevel() / threshold);
    }

    public static void applySettingsSnapshot(VoicePunishSettingsSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        loudThreshold = normalizeThreshold(snapshot.minimumVolumeThreshold(), "settings_snapshot");
    }

    private static double normalizeThreshold(double threshold, String source) {
        double normalized = Math.max(0.01D, Math.min(1D, threshold));
        if (Double.compare(threshold, normalized) != 0) {
            VoicePunishMod.LOGGER.warn(
                    "Normalized client loud threshold from {} to {} (source={})",
                    threshold,
                    normalized,
                    source
            );
        }
        return normalized;
    }
}
