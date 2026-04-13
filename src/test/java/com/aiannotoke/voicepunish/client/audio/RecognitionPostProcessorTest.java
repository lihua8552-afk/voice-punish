package com.aiannotoke.voicepunish.client.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionPostProcessorTest {

    private final RecognitionHintSet hintSet = RecognitionHintSet.compile(List.of("傻逼", "麦克风"), List.of());

    @Test
    void prepareCandidateTextRepairsChineseAliasesAndAsciiHints() {
        assertEquals("麦克风开挂", RecognitionPostProcessor.prepareCandidateText("卖克风 开怪", hintSet));
        assertEquals("麦克风", RecognitionPostProcessor.prepareCandidateText("mai ke feng", hintSet));
        assertEquals("服务器", RecognitionPostProcessor.prepareCandidateText("服武器", hintSet));
    }

    @Test
    void likelyNoiseRejectsShortAsciiWithoutHints() {
        String prepared = RecognitionPostProcessor.prepareCandidateText("abc", hintSet);
        String normalized = RecognitionPostProcessor.normalizeForScoring(prepared, hintSet);
        assertTrue(RecognitionPostProcessor.isLikelyNoise(prepared, normalized, hintSet));
    }
}
