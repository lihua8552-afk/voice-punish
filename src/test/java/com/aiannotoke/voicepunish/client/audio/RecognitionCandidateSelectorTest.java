package com.aiannotoke.voicepunish.client.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecognitionCandidateSelectorTest {

    @Test
    void prefersChineseHintRichCandidateOverAsciiNoise() {
        RecognitionHintSet hintSet = RecognitionHintSet.compile(List.of("麦克风", "开挂"), List.of());
        RecognitionResult result = RecognitionCandidateSelector.selectCandidates(
                List.of(
                        new TranscriptCandidate(TranscriptCandidate.Source.MAIN_ALTERNATIVE, "mai ke feng kai gua", "", 0.55D, 0, 0D),
                        new TranscriptCandidate(TranscriptCandidate.Source.SHRIEK_FALLBACK, "卖克风 开怪", "", 0.45D, 0, 0D)
                ),
                hintSet,
                List.of(),
                ""
        );

        assertNotNull(result);
        assertEquals("麦克风开挂", result.finalTranscript());
        assertEquals(TranscriptCandidate.Source.SHRIEK_FALLBACK, result.selectedCandidate().source());
    }

    @Test
    void dropsDuplicateAgainstLastSentNormalized() {
        RecognitionHintSet hintSet = RecognitionHintSet.compile(List.of("傻逼"), List.of());
        RecognitionResult result = RecognitionCandidateSelector.selectCandidates(
                List.of(
                        new TranscriptCandidate(TranscriptCandidate.Source.MAIN_FINAL, "傻逼", "", 1D, 0, 0D),
                        new TranscriptCandidate(TranscriptCandidate.Source.SHRIEK_FALLBACK, "麦克风", "", 0.7D, 0, 0D)
                ),
                hintSet,
                List.of(),
                "傻逼"
        );

        assertNotNull(result);
        assertEquals("麦克风", result.finalTranscript());
    }

    @Test
    void returnsNullWhenOnlyNoiseExists() {
        RecognitionHintSet hintSet = RecognitionHintSet.compile(List.of(), List.of());
        RecognitionResult result = RecognitionCandidateSelector.selectCandidates(
                List.of(new TranscriptCandidate(TranscriptCandidate.Source.MAIN_FINAL, "ab", "", 1D, 0, 0D)),
                hintSet,
                List.of(),
                ""
        );

        assertNull(result);
    }
}
