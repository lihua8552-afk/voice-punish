package com.aiannotoke.voicepunish.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTranscriptionEngineTest {

    @Test
    void loudPcmFrameCountsAsSpeechActivity() {
        byte[] pcm = new byte[] {(byte) 0xFF, 0x7F, (byte) 0xFF, 0x7F, 0, 0, 0, 0};
        assertTrue(ClientTranscriptionEngine.computeSpeechActivity(pcm) > ClientTranscriptionEngine.SPEECH_ACTIVITY_THRESHOLD);
    }

    @Test
    void fallbackOnlyEvidenceFinalizesAfterSilenceAndStall() {
        String reason = ClientTranscriptionEngine.decideIdleFinalizeReason(
                1_600L,
                true,
                1_100L,
                1_100L,
                Long.MIN_VALUE,
                Long.MIN_VALUE,
                1_100L,
                1_100L,
                "傻逼"
        );
        assertEquals("fallback_stall_timeout", reason);
    }

    @Test
    void sustainedSpeechDoesNotFinalizeEarly() {
        String reason = ClientTranscriptionEngine.decideIdleFinalizeReason(
                1_250L,
                true,
                1_240L,
                1_200L,
                1_230L,
                1_230L,
                1_230L,
                Long.MIN_VALUE,
                ""
        );
        assertNull(reason);
    }

    @Test
    void stalledRecognitionAfterSpeechStopsFinalizesOnSilenceTimeout() {
        String reason = ClientTranscriptionEngine.decideIdleFinalizeReason(
                2_000L,
                true,
                1_700L,
                1_500L,
                1_600L,
                1_650L,
                1_650L,
                Long.MIN_VALUE,
                ""
        );
        assertEquals("silence_timeout", reason);
    }

    @Test
    void providerMustBeHealthyBeforeRemoteTranscriptionRuns() {
        TranscriptionProvider provider = new TranscriptionProvider() {
            @Override
            public String providerId() {
                return "test";
            }

            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public java.util.concurrent.CompletableFuture<Boolean> refreshHealthAsync() {
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }

            @Override
            public java.util.concurrent.CompletableFuture<TranscriptionProviderResult> transcribeWavAsync(byte[] wavBytes, long durationMs) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("unavailable"));
            }

            @Override
            public void close() {
            }
        };

        assertFalse(ClientTranscriptionEngine.shouldUseProvider(provider, new byte[] {1, 2}));
    }
}
