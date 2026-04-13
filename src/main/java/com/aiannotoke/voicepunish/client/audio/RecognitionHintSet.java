package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.moderation.TextNormalizer;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecognitionHintSet {

    private static final Gson GSON = new Gson();
    private static final int MAX_GRAMMAR_TERMS = 192;
    private static final int MAX_GRAMMAR_TEXT_LENGTH = 8_192;
    private static final List<String> BUILTIN_HINTS = List.of(
            "麦克风",
            "服务器",
            "开挂",
            "外挂",
            "队友",
            "末地",
            "地狱",
            "传送",
            "苦力怕",
            "末影人",
            "僵尸",
            "骷髅",
            "语音",
            "违禁词",
            "音量",
            "Simple Voice Chat",
            "voice chat",
            "HUD"
    );
    private static final Map<String, String> LATIN_HINT_REPLACEMENTS = Map.ofEntries(
            Map.entry("maikefeng", "麦克风"),
            Map.entry("fuwuqi", "服务器"),
            Map.entry("kaigua", "开挂"),
            Map.entry("waigua", "外挂"),
            Map.entry("duiyou", "队友"),
            Map.entry("madi", "末地"),
            Map.entry("diyu", "地狱"),
            Map.entry("chuansong", "传送"),
            Map.entry("kulipa", "苦力怕"),
            Map.entry("moyingren", "末影人"),
            Map.entry("jiangshi", "僵尸"),
            Map.entry("kulou", "骷髅"),
            Map.entry("yuyin", "语音"),
            Map.entry("weijinci", "违禁词"),
            Map.entry("yinliang", "音量"),
            Map.entry("shabi", "傻逼"),
            Map.entry("shanbi", "傻逼"),
            Map.entry("caonima", "操你妈"),
            Map.entry("gunnima", "滚你妈"),
            Map.entry("ganliniang", "干你娘"),
            Map.entry("nmsl", "你妈死了")
    );

    private final List<String> hints;
    private final List<String> normalizedHints;
    private final String grammarJson;

    private RecognitionHintSet(List<String> hints, List<String> normalizedHints, String grammarJson) {
        this.hints = hints;
        this.normalizedHints = normalizedHints;
        this.grammarJson = grammarJson;
    }

    public static RecognitionHintSet compile(Collection<String> serverHints, Collection<String> clientHotWords) {
        LinkedHashSet<String> combined = new LinkedHashSet<>(BUILTIN_HINTS);
        addAll(combined, serverHints);
        addAll(combined, clientHotWords);

        List<String> hints = new ArrayList<>(combined);
        List<String> normalized = new ArrayList<>(hints.size());
        for (String hint : hints) {
            normalized.add(TextNormalizer.normalizePlain(hint));
        }

        List<String> grammarTerms = buildGrammarTerms(hints);
        return new RecognitionHintSet(List.copyOf(hints), List.copyOf(normalized), GSON.toJson(grammarTerms));
    }

    public List<String> hints() {
        return hints;
    }

    public String grammarJson() {
        return grammarJson;
    }

    public int countMatches(String normalizedTranscript) {
        if (normalizedTranscript == null || normalizedTranscript.isBlank()) {
            return 0;
        }

        int matches = 0;
        for (String normalizedHint : normalizedHints) {
            if (normalizedHint.isBlank()) {
                continue;
            }
            if (normalizedTranscript.contains(normalizedHint)) {
                matches++;
            }
        }
        return matches;
    }

    public String applyAsciiHintReplacements(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (isAsciiHintChar(current)) {
                int end = index;
                while (end < text.length() && isAsciiHintChar(text.charAt(end))) {
                    end++;
                }
                String segment = text.substring(index, end);
                String compact = compactAsciiSegment(segment);
                String replacement = LATIN_HINT_REPLACEMENTS.get(compact);
                builder.append(replacement == null ? segment : replacement);
                index = end;
                continue;
            }
            builder.append(current);
            index++;
        }
        return builder.toString();
    }

    private static void addAll(Set<String> target, Collection<String> source) {
        if (source == null) {
            return;
        }
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private static List<String> buildGrammarTerms(List<String> hints) {
        List<String> grammarTerms = new ArrayList<>();
        int estimatedLength = 2; // []
        for (String hint : hints) {
            if (hint == null) {
                continue;
            }
            String trimmed = hint.trim();
            if (trimmed.isEmpty() || trimmed.length() > 24) {
                continue;
            }
            if (TextNormalizer.normalizePlain(trimmed).length() < 2) {
                continue;
            }
            if (grammarTerms.size() >= MAX_GRAMMAR_TERMS) {
                break;
            }
            int nextEstimatedLength = estimatedLength + trimmed.length() + 4;
            if (nextEstimatedLength >= MAX_GRAMMAR_TEXT_LENGTH) {
                break;
            }
            grammarTerms.add(trimmed);
            estimatedLength = nextEstimatedLength;
        }
        grammarTerms.add("[unk]");
        return List.copyOf(grammarTerms);
    }

    private static boolean isAsciiHintChar(char current) {
        return current <= 127 && (Character.isLetterOrDigit(current) || Character.isWhitespace(current) || current == '-' || current == '_');
    }

    private static String compactAsciiSegment(String segment) {
        StringBuilder builder = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char current = Character.toLowerCase(segment.charAt(i));
            if (Character.isLetterOrDigit(current)) {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
