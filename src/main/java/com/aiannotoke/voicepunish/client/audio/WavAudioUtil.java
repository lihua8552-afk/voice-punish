package com.aiannotoke.voicepunish.client.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class WavAudioUtil {

    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = 2;
    public static final int BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE;

    private WavAudioUtil() {
    }

    public static byte[] wrapPcmAsWav(byte[] pcmBytes) {
        byte[] pcm = pcmBytes == null ? new byte[0] : pcmBytes;
        int dataLength = pcm.length;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(dataLength + 44)) {
            writeAscii(output, "RIFF");
            writeIntLE(output, 36 + dataLength);
            writeAscii(output, "WAVE");
            writeAscii(output, "fmt ");
            writeIntLE(output, 16);
            writeShortLE(output, (short) 1);
            writeShortLE(output, (short) CHANNELS);
            writeIntLE(output, SAMPLE_RATE);
            writeIntLE(output, BYTE_RATE);
            writeShortLE(output, (short) (CHANNELS * BYTES_PER_SAMPLE));
            writeShortLE(output, (short) BITS_PER_SAMPLE);
            writeAscii(output, "data");
            writeIntLE(output, dataLength);
            output.write(pcm);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to build WAV payload", exception);
        }
    }

    public static long pcmDurationMs(byte[] pcmBytes) {
        if (pcmBytes == null || pcmBytes.length == 0) {
            return 0L;
        }
        return Math.round((pcmBytes.length / (double) BYTE_RATE) * 1000D);
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(ByteArrayOutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream output, short value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }
}
