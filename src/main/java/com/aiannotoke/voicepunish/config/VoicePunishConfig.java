package com.aiannotoke.voicepunish.config;

import java.util.ArrayList;
import java.util.List;

public class VoicePunishConfig {

    public boolean enableVolumeModeration = true;
    public boolean enableTranscriptModeration = true;
    public boolean muteIncomingVoicePlayback = true;
    public boolean logAllTranscriptsToConsole = true;

    public long baselineWindowMs = 8_000L;
    public long rollingWindowMs = 500L;
    public long overThresholdHoldMs = 700L;
    public long loudCooldownMs = 20_000L;
    public double minimumVolumeThreshold = 0.18D;
    public double volumeMultiplier = 2.2D;
    public double baselineNoiseFloor = 0.015D;

    public long joinVoicechatGraceMs = 30_000L;
    public long disconnectedVoicechatGraceMs = 15_000L;
    public long transcriptGraceMs = 30_000L;
    public int transcriptMinVoicePackets = 60;
    public long duplicateTranscriptWindowMs = 3_000L;

    public float loudDamage = 4.0F;
    public float loudEscalatedDamage = 6.0F;
    public int loudEscalateAfter = 3;
    public long loudEscalateWindowMs = 5 * 60_000L;

    public float badWordDamage = 6.0F;
    public float badWordEscalatedDamage = 8.0F;
    public int badWordEscalateAfter = 2;
    public long badWordEscalateWindowMs = 10 * 60_000L;

    public int defaultEventCount = 1;
    public int escalatedEventCount = 2;

    public int hostileMobWeight = 30;
    public int inventoryLossWeight = 25;
    public int negativeEffectWeight = 25;
    public int teleportWeight = 20;

    public int mobSpawnMinDistance = 4;
    public int mobSpawnMaxDistance = 8;
    public int mobSpawnAttempts = 16;

    public int teleportMinRadius = 16;
    public int teleportMaxRadius = 48;
    public int teleportAttempts = 24;

    public int itemLossRetries = 10;
    public int effectMinSeconds = 15;
    public int effectMaxSeconds = 30;

    public List<String> badWords = new ArrayList<>(List.of(
            "测试违禁词",
            "傻逼",
            "傻比",
            "傻b",
            "煞笔",
            "傻批",
            "伞兵",
            "沙比",
            "sb",
            "s13",
            "脑残",
            "弱智",
            "智障",
            "废物",
            "废柴",
            "垃圾",
            "人渣",
            "狗东西",
            "狗叫",
            "畜生",
            "出生",
            "杂种",
            "蛆",
            "臭鱼烂虾",
            "小丑",
            "逆天",
            "真唐",
            "唐氏",
            "下头",
            "普信男",
            "普信女",
            "巨婴",
            "龟男",
            "捞女",
            "矮子",
            "矮冬瓜",
            "死矮子",
            "小矮子",
            "秃头",
            "死秃子",
            "地中海",
            "谢顶",
            "四眼",
            "死胖子",
            "肥猪",
            "装逼",
            "牛马",
            "人机",
            "菜狗",
            "老六",
            "挂逼",
            "开挂狗",
            "孤儿",
            "狗比",
            "妈逼",
            "妈的",
            "他妈的",
            "你妈",
            "死妈",
            "死全家",
            "滚你妈",
            "操你妈",
            "草泥马",
            "cnm",
            "nmsl",
            "tmd",
            "nm"
    ));

    public List<String> hostileMobs = new ArrayList<>(List.of(
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:spider",
            "minecraft:creeper"
    ));

    public List<String> negativeEffects = new ArrayList<>(List.of(
            "minecraft:slowness",
            "minecraft:weakness",
            "minecraft:poison",
            "minecraft:blindness",
            "minecraft:nausea",
            "minecraft:mining_fatigue"
    ));

    public VoicePunishConfig copy() {
        VoicePunishConfig copy = new VoicePunishConfig();
        copy.enableVolumeModeration = enableVolumeModeration;
        copy.enableTranscriptModeration = enableTranscriptModeration;
        copy.muteIncomingVoicePlayback = muteIncomingVoicePlayback;
        copy.logAllTranscriptsToConsole = logAllTranscriptsToConsole;
        copy.baselineWindowMs = baselineWindowMs;
        copy.rollingWindowMs = rollingWindowMs;
        copy.overThresholdHoldMs = overThresholdHoldMs;
        copy.loudCooldownMs = loudCooldownMs;
        copy.minimumVolumeThreshold = minimumVolumeThreshold;
        copy.volumeMultiplier = volumeMultiplier;
        copy.baselineNoiseFloor = baselineNoiseFloor;
        copy.joinVoicechatGraceMs = joinVoicechatGraceMs;
        copy.disconnectedVoicechatGraceMs = disconnectedVoicechatGraceMs;
        copy.transcriptGraceMs = transcriptGraceMs;
        copy.transcriptMinVoicePackets = transcriptMinVoicePackets;
        copy.duplicateTranscriptWindowMs = duplicateTranscriptWindowMs;
        copy.loudDamage = loudDamage;
        copy.loudEscalatedDamage = loudEscalatedDamage;
        copy.loudEscalateAfter = loudEscalateAfter;
        copy.loudEscalateWindowMs = loudEscalateWindowMs;
        copy.badWordDamage = badWordDamage;
        copy.badWordEscalatedDamage = badWordEscalatedDamage;
        copy.badWordEscalateAfter = badWordEscalateAfter;
        copy.badWordEscalateWindowMs = badWordEscalateWindowMs;
        copy.defaultEventCount = defaultEventCount;
        copy.escalatedEventCount = escalatedEventCount;
        copy.hostileMobWeight = hostileMobWeight;
        copy.inventoryLossWeight = inventoryLossWeight;
        copy.negativeEffectWeight = negativeEffectWeight;
        copy.teleportWeight = teleportWeight;
        copy.mobSpawnMinDistance = mobSpawnMinDistance;
        copy.mobSpawnMaxDistance = mobSpawnMaxDistance;
        copy.mobSpawnAttempts = mobSpawnAttempts;
        copy.teleportMinRadius = teleportMinRadius;
        copy.teleportMaxRadius = teleportMaxRadius;
        copy.teleportAttempts = teleportAttempts;
        copy.itemLossRetries = itemLossRetries;
        copy.effectMinSeconds = effectMinSeconds;
        copy.effectMaxSeconds = effectMaxSeconds;
        copy.badWords = new ArrayList<>(badWords);
        copy.hostileMobs = new ArrayList<>(hostileMobs);
        copy.negativeEffects = new ArrayList<>(negativeEffects);
        return copy;
    }

    public void fillDefaults() {
        if (badWords == null || badWords.isEmpty()) {
            badWords = new ArrayList<>(List.of(
                    "测试违禁词",
                    "傻逼",
                    "伞兵",
                    "脑残",
                    "废物",
                    "狗东西",
                    "小丑",
                    "逆天",
                    "真唐",
                    "人机",
                    "老六",
                    "挂逼",
                    "矮子",
                    "秃头",
                    "操你妈",
                    "nmsl"
            ));
        }
        if (hostileMobs == null || hostileMobs.isEmpty()) {
            hostileMobs = new ArrayList<>(List.of("minecraft:zombie"));
        }
        if (negativeEffects == null || negativeEffects.isEmpty()) {
            negativeEffects = new ArrayList<>(List.of("minecraft:slowness"));
        }
        baselineWindowMs = Math.max(1000L, baselineWindowMs);
        rollingWindowMs = Math.max(100L, rollingWindowMs);
        overThresholdHoldMs = Math.max(100L, overThresholdHoldMs);
        loudCooldownMs = Math.max(1000L, loudCooldownMs);
        joinVoicechatGraceMs = Math.max(5000L, joinVoicechatGraceMs);
        disconnectedVoicechatGraceMs = Math.max(5000L, disconnectedVoicechatGraceMs);
        transcriptGraceMs = Math.max(5000L, transcriptGraceMs);
        transcriptMinVoicePackets = Math.max(10, transcriptMinVoicePackets);
        duplicateTranscriptWindowMs = Math.max(500L, duplicateTranscriptWindowMs);
        loudEscalateAfter = Math.max(1, loudEscalateAfter);
        badWordEscalateAfter = Math.max(1, badWordEscalateAfter);
        defaultEventCount = Math.max(1, defaultEventCount);
        escalatedEventCount = Math.max(defaultEventCount, escalatedEventCount);
        loudDamage = Math.max(0F, loudDamage);
        loudEscalatedDamage = Math.max(0F, loudEscalatedDamage);
        badWordDamage = Math.max(0F, badWordDamage);
        badWordEscalatedDamage = Math.max(0F, badWordEscalatedDamage);
        hostileMobWeight = Math.max(0, hostileMobWeight);
        inventoryLossWeight = Math.max(0, inventoryLossWeight);
        negativeEffectWeight = Math.max(0, negativeEffectWeight);
        teleportWeight = Math.max(0, teleportWeight);
        if (hostileMobWeight + inventoryLossWeight + negativeEffectWeight + teleportWeight <= 0) {
            hostileMobWeight = 30;
            inventoryLossWeight = 25;
            negativeEffectWeight = 25;
            teleportWeight = 20;
        }
        mobSpawnAttempts = Math.max(4, mobSpawnAttempts);
        teleportAttempts = Math.max(4, teleportAttempts);
        itemLossRetries = Math.max(1, itemLossRetries);
        effectMinSeconds = Math.max(1, effectMinSeconds);
        effectMaxSeconds = Math.max(effectMinSeconds, effectMaxSeconds);
        minimumVolumeThreshold = Math.max(0.01D, Math.min(1D, minimumVolumeThreshold));
        volumeMultiplier = Math.max(0.1D, Math.min(10D, volumeMultiplier));
    }
}
