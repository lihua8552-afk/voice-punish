package com.aiannotoke.voicepunish.client.audio;

import java.util.List;

public record TranscriptionProviderResult(
        String provider,
        String model,
        String text,
        String sentenceText,
        Long durationMs,
        Double confidence,
        List<Segment> segments
) {

    public TranscriptionProviderResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public String preferredText(boolean preferSentenceText) {
        if (preferSentenceText && sentenceText != null && !sentenceText.isBlank()) {
            return sentenceText;
        }
        return text == null ? "" : text;
    }

    public record Segment(String text, Long startMs, Long endMs) {
    }
}
