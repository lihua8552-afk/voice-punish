package com.aiannotoke.voicepunish.moderation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TranscriptEnhancer {

    private static final List<String> RECOGNITION_HINT_TERMS = List.of(
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
            "音量"
    );

    private static final List<ReplacementRule> DISPLAY_RULES = List.of(
            rule("麦克风", "卖克风", "迈克风", "麦可风", "麦客风"),
            rule("服务器", "服武器", "服无器", "服物器"),
            rule("开挂", "开卦", "开怪"),
            rule("外挂", "外卦", "外怪"),
            rule("挂逼", "挂比", "挂壁"),
            rule("队友", "对友"),
            rule("老六", "老6", "老溜"),
            rule("卡顿", "卡遁", "卡吨"),
            rule("爆头", "报头"),
            rule("打野", "大爷"),
            rule("补刀", "补到"),
            rule("傻逼", "傻比", "煞笔", "煞比", "傻批", "沙比", "莎比", "啥比", "啥逼"),
            rule("操你妈", "草泥马", "草你妈", "曹尼玛", "曹你妈", "槽你妈", "操尼玛", "艹你妈"),
            rule("滚你妈", "滚尼玛", "滚你吗"),
            rule("脑残", "脑惨", "脑餐"),
            rule("弱智", "若智"),
            rule("智障", "制杖"),
            rule("废物", "费物"),
            rule("狗东西", "够东西"),
            rule("秃子", "图子", "突子", "秃紫"),
            rule("普信男", "普信男", "普性男"),
            rule("普信女", "普信女", "普性女"),
            rule("龟男", "闺男"),
            rule("人机", "仁姬", "人鸡"),
            rule("菜狗", "菜苟"),
            rule("牛马", "流马")
    );

    private static final List<ReplacementRule> MODERATION_RULES = List.of(
            rule("傻逼", "傻比", "煞笔", "煞比", "傻批", "沙比", "莎比", "啥比", "啥逼", "鲨臂", "鲨比"),
            rule("操你妈", "草泥马", "草你妈", "曹尼玛", "曹你妈", "槽你妈", "操尼玛", "艹你妈", "草拟吗"),
            rule("滚你妈", "滚尼玛", "滚你吗"),
            rule("死全家", "四全家"),
            rule("脑残", "脑惨", "脑餐"),
            rule("弱智", "若智"),
            rule("智障", "制杖"),
            rule("废物", "费物"),
            rule("狗东西", "够东西"),
            rule("畜生", "出生"),
            rule("秃子", "图子", "突子", "秃紫"),
            rule("挂逼", "挂比", "挂壁"),
            rule("老六", "老6", "老溜"),
            rule("人机", "仁姬", "人鸡"),
            rule("菜狗", "菜苟"),
            rule("牛马", "流马"),
            rule("妈逼", "马逼", "麻痹"),
            rule("狗比", "狗b", "狗币"),
            rule("孤儿", "估儿"),
            rule("普信男", "普信男", "普性男"),
            rule("普信女", "普信女", "普性女"),
            rule("龟男", "闺男"),
            rule("捞女", "老女"),
            rule("真唐", "真糖"),
            rule("唐氏", "糖氏"),
            rule("逆天", "逆天儿")
    );

    private TranscriptEnhancer() {
    }

    public static String enhanceTranscript(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String enhanced = collapseChineseSpacing(text);
        enhanced = applyRules(enhanced, DISPLAY_RULES);
        return collapseChineseSpacing(enhanced);
    }

    public static List<String> expandVariants(String word) {
        if (word == null || word.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(word.trim());

        for (ReplacementRule rule : MODERATION_RULES) {
            if (rule.matches(word)) {
                variants.add(rule.canonical());
                variants.addAll(rule.aliases());
            }
        }

        return List.copyOf(variants);
    }

    public static List<String> expandRecognitionVariants(String word) {
        if (word == null || word.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> variants = new LinkedHashSet<>(expandVariants(word));
        for (ReplacementRule rule : DISPLAY_RULES) {
            if (rule.matches(word)) {
                variants.add(rule.canonical());
                variants.addAll(rule.aliases());
            }
        }
        return List.copyOf(variants);
    }

    public static List<String> recognitionHintTerms() {
        return RECOGNITION_HINT_TERMS;
    }

    private static String applyRules(String text, List<ReplacementRule> rules) {
        String result = text;
        for (ReplacementRule rule : rules) {
            result = rule.apply(result);
        }
        return result;
    }

    private static String collapseChineseSpacing(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isWhitespace(current)) {
                char previous = previousNonWhitespace(text, i);
                char next = nextNonWhitespace(text, i);
                if (isChinese(previous) && isChinese(next)) {
                    continue;
                }
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                continue;
            }
            builder.append(current);
        }
        return builder.toString().trim();
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

    private static boolean isChinese(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static ReplacementRule rule(String canonical, String... aliases) {
        return new ReplacementRule(canonical, List.of(aliases));
    }

    private record ReplacementRule(String canonical, List<String> aliases) {

        private boolean matches(String value) {
            if (canonical.equals(value)) {
                return true;
            }
            return aliases.contains(value);
        }

        private String apply(String text) {
            String result = text;
            for (String alias : aliases) {
                result = result.replace(alias, canonical);
            }
            return result;
        }
    }
}
