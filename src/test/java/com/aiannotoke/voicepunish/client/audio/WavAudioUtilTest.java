package com.aiannotoke.voicepunish.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavAudioUtilTest {

    @Test
    void wrapsPcmIntoValidSizedWav() {
        byte[] pcm = new byte[WavAudioUtil.BYTE_RATE];
        byte[] wav = WavAudioUtil.wrapPcmAsWav(pcm);

        assertEquals(44 + pcm.length, wav.length);
        assertEquals('R', wav[0]);
        assertEquals('I', wav[1]);
        assertEquals('F', wav[2]);
        assertEquals('F', wav[3]);
        assertEquals(1000L, WavAudioUtil.pcmDurationMs(pcm));
    }

    @Test
    void emptyPcmHasZeroDuration() {
        assertEquals(0L, WavAudioUtil.pcmDurationMs(new byte[0]));
        assertTrue(WavAudioUtil.wrapPcmAsWav(new byte[0]).length >= 44);
    }
}
