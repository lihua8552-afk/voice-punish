package com.aiannotoke.voicepunish.moderation;

import java.util.List;

public record TranscriptMatchResult(
        String rawText,
        String normalizedText,
        List<String> matchedWords,
        List<HighlightRange> highlightRanges
) {

    public boolean hasMatches() {
        return !matchedWords.isEmpty();
    }

    public static TranscriptMatchResult synthetic(String rawText, String normalizedText, String matchedWord) {
        int index = rawText.indexOf(matchedWord);
        List<HighlightRange> ranges = index >= 0
                ? List.of(new HighlightRange(index, index + matchedWord.length()))
                : List.of();
        return new TranscriptMatchResult(rawText, normalizedText, List.of(matchedWord), ranges);
    }

    public record HighlightRange(int startInclusive, int endExclusive) {
    }
}
