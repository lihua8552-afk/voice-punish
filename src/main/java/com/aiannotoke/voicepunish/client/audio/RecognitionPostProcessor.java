package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.moderation.TextNormalizer;
import com.aiannotoke.voicepunish.moderation.TranscriptEnhancer;
import com.aiannotoke.voicepunish.util.TextRepairUtil;

import java.text.Normalizer;

public final class RecognitionPostProcessor {

    private RecognitionPostProcessor() {
    }

    public static String prepareCandidateText(String text, RecognitionHintSet hintSet) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String repaired = TextRepairUtil.repairIfNeeded(text);
        if (repaired == null || repaired.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(repaired, Normalizer.Form.NFKC);
        normalized = stripInvisible(normalized);
        normalized = TranscriptEnhancer.enhanceTranscript(normalized);
        normalized = hintSet.applyAsciiHintReplacements(normalized);
        normalized = TranscriptEnhancer.enhanceTranscript(normalized);
        return normalized.trim();
    }

    public static String normalizeForScoring(String text, RecognitionHintSet hintSet) {
        return TextNormalizer.normalizePlain(prepareCandidateText(text, hintSet));
    }

    public static boolean isLikelyNoise(String preparedText, String normalizedText, RecognitionHintSet hintSet) {
        if (preparedText == null || preparedText.isBlank() || normalizedText == null || normalizedText.isBlank()) {
            return true;
        }
        if (TextRepairUtil.looksLikeMojibake(preparedText)) {
            return true;
        }
        if (containsChinese(preparedText)) {
            return normalizedText.length() <= 1 && hintSet.countMatches(normalizedText) == 0;
        }
        if (normalizedText.length() <= 2 && hintSet.countMatches(normalizedText) == 0) {
            return true;
        }
        return isPureAsciiLetters(preparedText) && preparedText.length() <= 3 && hintSet.countMatches(normalizedText) == 0;
    }

    public static double chineseRatio(String text) {
        if (text == null || text.isBlank()) {
            return 0D;
        }
        int chineseCount = 0;
        int acceptedCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            acceptedCount++;
            if (isChinese(current)) {
                chineseCount++;
            }
        }
        return acceptedCount <= 0 ? 0D : (double) chineseCount / acceptedCount;
    }

    public static boolean containsChinese(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isChinese(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPureAsciiLetters(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current > 127 || !Character.isLetter(current)) {
                return false;
            }
        }
        return true;
    }

    private static String stripInvisible(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.getType(current) == Character.FORMAT || current == '\uFEFF' || current == '\u200B') {
                continue;
            }
            if (Character.isISOControl(current) && !Character.isWhitespace(current)) {
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static boolean isChinese(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
