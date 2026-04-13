package com.aiannotoke.voicepunish.moderation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static TranscriptMatchResult analyze(String rawText, Collection<String> badWords) {
        NormalizedText normalizedText = normalize(rawText);
        Set<String> matchedWords = new LinkedHashSet<>();
        List<TranscriptMatchResult.HighlightRange> ranges = new ArrayList<>();

        for (String word : badWords) {
            for (String variant : TranscriptEnhancer.expandVariants(word)) {
                NormalizedText normalizedWord = normalize(variant);
                if (normalizedWord.normalized().isEmpty()) {
                    continue;
                }
                int index = normalizedText.normalized().indexOf(normalizedWord.normalized());
                while (index >= 0) {
                    matchedWords.add(normalize(word).normalized());
                    int start = normalizedText.normalizedToRawIndex().get(index);
                    int end = normalizedText.normalizedToRawIndex().get(index + normalizedWord.normalized().length() - 1) + 1;
                    ranges.add(new TranscriptMatchResult.HighlightRange(start, end));
                    index = normalizedText.normalized().indexOf(normalizedWord.normalized(), index + 1);
                }
            }
        }

        return new TranscriptMatchResult(rawText, normalizedText.normalized(), List.copyOf(matchedWords), mergeRanges(ranges));
    }

    public static String normalizePlain(String rawText) {
        return normalize(rawText).normalized();
    }

    private static NormalizedText normalize(String rawText) {
        StringBuilder builder = new StringBuilder();
        List<Integer> mapping = new ArrayList<>();

        for (int i = 0; i < rawText.length(); i++) {
            char current = normalizeChar(rawText.charAt(i));
            if (isAccepted(current)) {
                builder.append(current);
                mapping.add(i);
            }
        }
        return new NormalizedText(builder.toString(), mapping);
    }

    private static List<TranscriptMatchResult.HighlightRange> mergeRanges(List<TranscriptMatchResult.HighlightRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        ranges.sort((left, right) -> Integer.compare(left.startInclusive(), right.startInclusive()));
        List<TranscriptMatchResult.HighlightRange> merged = new ArrayList<>();
        TranscriptMatchResult.HighlightRange current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            TranscriptMatchResult.HighlightRange next = ranges.get(i);
            if (next.startInclusive() <= current.endExclusive()) {
                current = new TranscriptMatchResult.HighlightRange(
                        current.startInclusive(),
                        Math.max(current.endExclusive(), next.endExclusive())
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static char normalizeChar(char current) {
        if (current == '\u3000') {
            return ' ';
        }
        if (current >= '\uFF01' && current <= '\uFF5E') {
            current = (char) (current - 0xFEE0);
        }
        return Character.toLowerCase(current);
    }

    private static boolean isAccepted(char current) {
        if (Character.isWhitespace(current) || current == '_') {
            return false;
        }
        if (Character.isLetterOrDigit(current)) {
            return true;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private record NormalizedText(String normalized, List<Integer> normalizedToRawIndex) {
    }
}
