package com.aiannotoke.voicepunish.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TextRepairUtil {

    private static final Charset GBK = Charset.forName("GBK");
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Charset BIG5 = Charset.forName("Big5");
    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
    private static final String COMMON_SIMPLIFIED_CHARS = "的一是在不了有人这中大来上个们到说国和地也子时道出而要于就下得可你年生自会那后能对着事其里所去行过家十用发天如然作方成者多日都三小军二无同么经法当起与好看学进种将还分此心前面又定见只主没公从知全点实业外开它因由其些然前里后给使两本";
    private static final String MOJIBAKE_HINT_CHARS = "锟烫鈥銆鎴鍙缁鐨鍚闂鏂鏈浠閫冨喖鐡夊弸灞闊噺";
    private static final String[] MOJIBAKE_HINT_SEQUENCES = {
            "锟",
            "鎴",
            "缁",
            "冨",
            "鍙",
            "鐡",
            "鏈嬪",
            "闊抽",
            "鏂囧",
            "鍚庡",
            "鐨勯",
            "銆",
            "鈥",
            "锛"
    };

    private TextRepairUtil() {
    }

    public static String repairIfNeeded(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String best = pickBestCandidate(List.of(
                text,
                tryRepair(text, GBK, StandardCharsets.UTF_8),
                tryRepair(text, GB18030, StandardCharsets.UTF_8),
                tryRepair(text, BIG5, StandardCharsets.UTF_8),
                tryRepair(text, LATIN1, StandardCharsets.UTF_8),
                tryRepair(text, StandardCharsets.UTF_8, GBK),
                tryRepair(text, StandardCharsets.UTF_8, GB18030),
                tryRepair(text, StandardCharsets.UTF_8, BIG5)
        ));

        if (qualityScore(best) < 0 || looksLikeMojibake(best)) {
            return "";
        }
        return best;
    }

    public static String pickBestCandidate(Iterable<String> candidates) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String normalized = normalizeSpacing(candidate);
            if (!normalized.isBlank()) {
                distinct.add(normalized);
            }
        }

        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (String candidate : distinct) {
            int candidateScore = qualityScore(candidate);
            if (candidateScore > bestScore) {
                best = candidate;
                bestScore = candidateScore;
            }
        }
        return best;
    }

    public static int qualityScore(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE / 2;
        }

        int score = 0;
        int hanCount = 0;
        int suspiciousCount = 0;
        int commonCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (COMMON_SIMPLIFIED_CHARS.indexOf(current) >= 0) {
                score += 8;
                hanCount++;
                commonCount++;
            } else if (isCommonChinese(current)) {
                score += 3;
                hanCount++;
            } else if (isReadableAscii(current)) {
                score += 1;
            } else if (isChinesePunctuation(current)) {
                score += 2;
            } else if (current == '\uFFFD') {
                score -= 14;
                suspiciousCount += 3;
            } else {
                score -= 4;
            }

            if (MOJIBAKE_HINT_CHARS.indexOf(current) >= 0) {
                score -= 7;
                suspiciousCount++;
            }
        }

        for (String sequence : MOJIBAKE_HINT_SEQUENCES) {
            score -= countOccurrences(text, sequence) * 12;
        }

        score += Math.min(20, commonCount * 2);
        if (hanCount > 0 && suspiciousCount * 2 >= hanCount) {
            score -= 40;
        }
        if (text.indexOf('\uFFFD') >= 0) {
            score -= 30;
        }

        return score;
    }

    public static boolean looksLikeMojibake(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int hanCount = 0;
        int suspiciousCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (isCommonChinese(current)) {
                hanCount++;
            }
            if (MOJIBAKE_HINT_CHARS.indexOf(current) >= 0 || current == '\uFFFD') {
                suspiciousCount++;
            }
        }
        return suspiciousCount >= 2 && suspiciousCount * 2 >= Math.max(hanCount, 1);
    }

    private static String tryRepair(String text, Charset source, Charset target) {
        try {
            return new String(text.getBytes(source), target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeSpacing(String text) {
        List<Character> output = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isISOControl(current) && !Character.isWhitespace(current)) {
                continue;
            }
            if (Character.isWhitespace(current)) {
                char previous = previousNonWhitespace(text, i);
                char next = nextNonWhitespace(text, i);
                if (isCommonChinese(previous) && isCommonChinese(next)) {
                    continue;
                }
                if (!output.isEmpty() && output.get(output.size() - 1) == ' ') {
                    continue;
                }
                output.add(' ');
                continue;
            }
            output.add(current == '\u3000' ? ' ' : current);
        }

        int start = 0;
        int end = output.size();
        while (start < end && output.get(start) == ' ') {
            start++;
        }
        while (end > start && output.get(end - 1) == ' ') {
            end--;
        }

        StringBuilder builder = new StringBuilder(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            builder.append(output.get(i));
        }
        return builder.toString();
    }

    private static char previousNonWhitespace(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char current = text.charAt(i);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private static char nextNonWhitespace(String text, int index) {
        for (int i = index + 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = text.indexOf(fragment);
        while (index >= 0) {
            count++;
            index = text.indexOf(fragment, index + fragment.length());
        }
        return count;
    }

    private static boolean isReadableAscii(char current) {
        return current == ' '
                || current == ':'
                || current == '-'
                || current == '_'
                || current == ','
                || current == '.'
                || current == '!'
                || current == '?'
                || Character.isLetterOrDigit(current);
    }

    private static boolean isChinesePunctuation(char current) {
        return "，。！？；：、“”‘’（）《》【】".indexOf(current) >= 0;
    }

    private static boolean isCommonChinese(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
