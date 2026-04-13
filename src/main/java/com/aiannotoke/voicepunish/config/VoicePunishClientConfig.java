package com.aiannotoke.voicepunish.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class VoicePunishClientConfig {

    public String transcriptionProvider = "local_funasr";
    public String localAsrBaseUrl = "http://127.0.0.1:47831";
    public int localAsrTimeoutMs = 4_000;
    public boolean preferSentenceText = true;
    public boolean allowProviderFallback = true;
    public String preferredModelName = "vosk-model-cn-0.22";
    public String fallbackModelName = "vosk-model-small-cn-0.22";
    public boolean enableHintRecognizer = true;
    public int maxAlternatives = 3;
    public boolean debugRecognition = false;
    public List<String> customHotWords = new ArrayList<>();

    public VoicePunishClientConfig copy() {
        VoicePunishClientConfig copy = new VoicePunishClientConfig();
        copy.transcriptionProvider = transcriptionProvider;
        copy.localAsrBaseUrl = localAsrBaseUrl;
        copy.localAsrTimeoutMs = localAsrTimeoutMs;
        copy.preferSentenceText = preferSentenceText;
        copy.allowProviderFallback = allowProviderFallback;
        copy.preferredModelName = preferredModelName;
        copy.fallbackModelName = fallbackModelName;
        copy.enableHintRecognizer = enableHintRecognizer;
        copy.maxAlternatives = maxAlternatives;
        copy.debugRecognition = debugRecognition;
        copy.customHotWords = new ArrayList<>(customHotWords);
        return copy;
    }

    public void fillDefaults() {
        transcriptionProvider = sanitizeProvider(transcriptionProvider);
        localAsrBaseUrl = sanitizeUrl(localAsrBaseUrl, "http://127.0.0.1:47831");
        localAsrTimeoutMs = Math.max(500, Math.min(30_000, localAsrTimeoutMs));
        preferredModelName = sanitizeModelName(preferredModelName, "vosk-model-cn-0.22");
        fallbackModelName = sanitizeModelName(fallbackModelName, "vosk-model-small-cn-0.22");
        maxAlternatives = Math.max(1, Math.min(5, maxAlternatives));

        LinkedHashSet<String> cleanedHotWords = new LinkedHashSet<>();
        if (customHotWords != null) {
            for (String hotWord : customHotWords) {
                if (hotWord == null) {
                    continue;
                }
                String trimmed = hotWord.trim();
                if (!trimmed.isEmpty()) {
                    cleanedHotWords.add(trimmed);
                }
            }
        }
        customHotWords = new ArrayList<>(cleanedHotWords);
    }

    private String sanitizeModelName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String sanitizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return "local_funasr";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "local_funasr", "vosk_fallback" -> normalized;
            default -> "local_funasr";
        };
    }

    private String sanitizeUrl(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
