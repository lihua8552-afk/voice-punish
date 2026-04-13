package com.aiannotoke.voicepunish.client.audio;

import java.util.concurrent.CompletableFuture;

public interface TranscriptionProvider extends AutoCloseable {

    String providerId();

    boolean isConfigured();

    boolean isAvailable();

    CompletableFuture<Boolean> refreshHealthAsync();

    CompletableFuture<TranscriptionProviderResult> transcribeWavAsync(byte[] wavBytes, long durationMs);

    @Override
    void close();
}
