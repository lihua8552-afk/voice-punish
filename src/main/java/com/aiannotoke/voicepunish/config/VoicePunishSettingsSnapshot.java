package com.aiannotoke.voicepunish.config;

import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record VoicePunishSettingsSnapshot(
        boolean enableVolumeModeration,
        boolean enableTranscriptModeration,
        double minimumVolumeThreshold,
        double volumeMultiplier,
        long overThresholdHoldMs,
        long loudCooldownMs,
        float loudDamage,
        float loudEscalatedDamage,
        float badWordDamage,
        float badWordEscalatedDamage,
        int defaultEventCount,
        int escalatedEventCount,
        int hostileMobWeight,
        int inventoryLossWeight,
        int negativeEffectWeight,
        int teleportWeight,
        List<String> badWords
) {

    public VoicePunishSettingsSnapshot {
        badWords = List.copyOf(sanitizeBadWords(badWords));
    }

    public static VoicePunishSettingsSnapshot fromConfig(VoicePunishConfig config) {
        return new VoicePunishSettingsSnapshot(
                config.enableVolumeModeration,
                config.enableTranscriptModeration,
                config.minimumVolumeThreshold,
                config.volumeMultiplier,
                config.overThresholdHoldMs,
                config.loudCooldownMs,
                config.loudDamage,
                config.loudEscalatedDamage,
                config.badWordDamage,
                config.badWordEscalatedDamage,
                config.defaultEventCount,
                config.escalatedEventCount,
                config.hostileMobWeight,
                config.inventoryLossWeight,
                config.negativeEffectWeight,
                config.teleportWeight,
                config.badWords
        );
    }

    public static VoicePunishSettingsSnapshot read(RegistryByteBuf buf) {
        int size = buf.readInt();
        List<String> badWords = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            badWords.add(buf.readString());
        }

        return new VoicePunishSettingsSnapshot(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readLong(),
                buf.readLong(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                badWords
        );
    }

    public void write(RegistryByteBuf buf) {
        buf.writeInt(badWords.size());
        for (String badWord : badWords) {
            buf.writeString(badWord);
        }

        buf.writeBoolean(enableVolumeModeration);
        buf.writeBoolean(enableTranscriptModeration);
        buf.writeDouble(minimumVolumeThreshold);
        buf.writeDouble(volumeMultiplier);
        buf.writeLong(overThresholdHoldMs);
        buf.writeLong(loudCooldownMs);
        buf.writeFloat(loudDamage);
        buf.writeFloat(loudEscalatedDamage);
        buf.writeFloat(badWordDamage);
        buf.writeFloat(badWordEscalatedDamage);
        buf.writeInt(defaultEventCount);
        buf.writeInt(escalatedEventCount);
        buf.writeInt(hostileMobWeight);
        buf.writeInt(inventoryLossWeight);
        buf.writeInt(negativeEffectWeight);
        buf.writeInt(teleportWeight);
    }

    public VoicePunishConfig applyTo(VoicePunishConfig config) {
        config.enableVolumeModeration = enableVolumeModeration;
        config.enableTranscriptModeration = enableTranscriptModeration;
        config.minimumVolumeThreshold = minimumVolumeThreshold;
        config.volumeMultiplier = volumeMultiplier;
        config.overThresholdHoldMs = overThresholdHoldMs;
        config.loudCooldownMs = loudCooldownMs;
        config.loudDamage = loudDamage;
        config.loudEscalatedDamage = loudEscalatedDamage;
        config.badWordDamage = badWordDamage;
        config.badWordEscalatedDamage = badWordEscalatedDamage;
        config.defaultEventCount = defaultEventCount;
        config.escalatedEventCount = escalatedEventCount;
        config.hostileMobWeight = hostileMobWeight;
        config.inventoryLossWeight = inventoryLossWeight;
        config.negativeEffectWeight = negativeEffectWeight;
        config.teleportWeight = teleportWeight;
        config.badWords = new ArrayList<>(sanitizeBadWords(badWords));
        config.fillDefaults();
        return config;
    }

    public VoicePunishSettingsSnapshot withBadWords(List<String> newBadWords) {
        return new VoicePunishSettingsSnapshot(
                enableVolumeModeration,
                enableTranscriptModeration,
                minimumVolumeThreshold,
                volumeMultiplier,
                overThresholdHoldMs,
                loudCooldownMs,
                loudDamage,
                loudEscalatedDamage,
                badWordDamage,
                badWordEscalatedDamage,
                defaultEventCount,
                escalatedEventCount,
                hostileMobWeight,
                inventoryLossWeight,
                negativeEffectWeight,
                teleportWeight,
                newBadWords
        );
    }

    public String validate() {
        if (minimumVolumeThreshold <= 0D || minimumVolumeThreshold > 1D) {
            return "音量阈值必须在 0 到 1 之间";
        }
        if (volumeMultiplier < 0.1D || volumeMultiplier > 10D) {
            return "音量倍数需要在 0.1 到 10 之间";
        }
        if (overThresholdHoldMs < 100L || overThresholdHoldMs > 60_000L) {
            return "超阈值持续时间需要在 100 到 60000 毫秒之间";
        }
        if (loudCooldownMs < 0L || loudCooldownMs > 600_000L) {
            return "超音量冷却时间需要在 0 到 600000 毫秒之间";
        }
        if (loudDamage < 0F || loudEscalatedDamage < 0F || badWordDamage < 0F || badWordEscalatedDamage < 0F) {
            return "扣血量不能为负数";
        }
        if (defaultEventCount < 1 || defaultEventCount > 10) {
            return "默认事件次数需要在 1 到 10 之间";
        }
        if (escalatedEventCount < defaultEventCount || escalatedEventCount > 10) {
            return "升级事件次数不能小于默认次数，且不能超过 10";
        }
        if (hostileMobWeight < 0 || inventoryLossWeight < 0 || negativeEffectWeight < 0 || teleportWeight < 0) {
            return "随机事件权重不能是负数";
        }
        if (hostileMobWeight + inventoryLossWeight + negativeEffectWeight + teleportWeight <= 0) {
            return "至少需要为一种随机事件设置大于 0 的权重";
        }
        if (sanitizeBadWords(badWords).isEmpty()) {
            return "违禁词列表不能为空";
        }
        return null;
    }

    public int totalWeight() {
        return hostileMobWeight + inventoryLossWeight + negativeEffectWeight + teleportWeight;
    }

    public double normalizedChance(int weight) {
        int totalWeight = totalWeight();
        if (totalWeight <= 0) {
            return 0D;
        }
        return (weight * 100D) / totalWeight;
    }

    public static List<String> sanitizeBadWords(List<String> input) {
        if (input == null) {
            return List.of();
        }

        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String word : input) {
            if (word == null) {
                continue;
            }
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return List.copyOf(cleaned);
    }
}
