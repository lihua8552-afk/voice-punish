package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishClientConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalFunAsrProvider implements TranscriptionProvider {

    private final VoicePunishClientConfig config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final EmbeddedAsrServiceManager embeddedServiceManager;
    private final AtomicBoolean healthy = new AtomicBoolean();

    public LocalFunAsrProvider(VoicePunishClientConfig config) {
        this.config = config.copy();
        this.config.fillDefaults();
        this.embeddedServiceManager = new EmbeddedAsrServiceManager(this.config);
        this.executor = Executors.newSingleThreadExecutor(new ProviderThreadFactory());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.config.localAsrTimeoutMs))
                .executor(executor)
                .build();
        this.embeddedServiceManager.initializeAsync();
    }

    @Override
    public String providerId() {
        return "local_funasr";
    }

    @Override
    public boolean isConfigured() {
        return "local_funasr".equals(config.transcriptionProvider);
    }

    @Override
    public boolean isAvailable() {
        return isConfigured() && healthy.get();
    }

    @Override
    public CompletableFuture<Boolean> refreshHealthAsync() {
        if (!isConfigured()) {
            healthy.set(false);
            ClientAsrRuntimeState.setDisabled("provider disabled");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> ensureRunning = embeddedServiceManager.shouldManage()
                ? embeddedServiceManager.ensureRunningAsync()
                : CompletableFuture.completedFuture(false);
        return ensureRunning
                .handle((ignored, throwable) -> null)
                .thenCompose(ignored -> sendHealthRequest());
    }

    @Override
    public CompletableFuture<TranscriptionProviderResult> transcribeWavAsync(byte[] wavBytes, long durationMs) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Local FunASR provider is disabled"));
        }
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Local FunASR provider is not healthy"));
        }

        HttpRequest request = HttpRequest.newBuilder(baseUri("/v1/transcribe"))
                .timeout(Duration.ofMillis(config.localAsrTimeoutMs))
                .header("Content-Type", "audio/wav")
                .header("X-VoicePunish-Duration-Ms", Long.toString(durationMs))
                .POST(HttpRequest.BodyPublishers.ofByteArray(wavBytes))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Local FunASR provider returned HTTP " + response.statusCode());
                    }
                    healthy.set(true);
                    return parseResult(response.body());
                })
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        healthy.set(false);
                        VoicePunishMod.LOGGER.warn("Local FunASR provider request failed", throwable);
                    }
                });
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    @Override
    public void close() {
        embeddedServiceManager.close();
        executor.shutdownNow();
    }

    private URI baseUri(String path) {
        String base = config.localAsrBaseUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    private CompletableFuture<Boolean> sendHealthRequest() {
        HttpRequest request = HttpRequest.newBuilder(baseUri("/healthz"))
                .timeout(Duration.ofMillis(config.localAsrTimeoutMs))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
                    healthy.set(ok);
                    if (ok) {
                        ClientAsrRuntimeState.setReady("ready");
                    } else {
                        String detail = extractHealthError(response.body());
                        if (detail.isBlank()) {
                            ClientAsrRuntimeState.setStarting("loading model");
                        } else {
                            ClientAsrRuntimeState.setError(detail);
                        }
                    }
                    return ok;
                })
                .exceptionally(exception -> {
                    healthy.set(false);
                    ClientAsrRuntimeState.setStarting("waiting for local service");
                    return false;
                });
    }

    private String extractHealthError(String body) {
        try {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            String error = getString(object, "error");
            if (error.isBlank()) {
                return "";
            }
            String lowered = error.toLowerCase();
            if (lowered.contains("modulenotfounderror") || lowered.contains("filenotfounderror")) {
                return "offline runtime is incomplete";
            }
            if (lowered.contains("cuda")) {
                return "gpu runtime unavailable";
            }
            return "model load failed";
        } catch (Exception ignored) {
            return "";
        }
    }

    private TranscriptionProviderResult parseResult(String body) {
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        String provider = getString(object, "provider");
        String model = getString(object, "model");
        String text = getString(object, "text");
        String sentenceText = getString(object, "sentence_text");
        Long durationMs = object.has("duration_ms") && !object.get("duration_ms").isJsonNull()
                ? object.get("duration_ms").getAsLong()
                : null;
        Double confidence = object.has("confidence") && !object.get("confidence").isJsonNull()
                ? object.get("confidence").getAsDouble()
                : null;

        List<TranscriptionProviderResult.Segment> segments = new ArrayList<>();
        if (object.has("segments") && object.get("segments").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("segments");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject segment = element.getAsJsonObject();
                segments.add(new TranscriptionProviderResult.Segment(
                        getString(segment, "text"),
                        segment.has("start_ms") && !segment.get("start_ms").isJsonNull() ? segment.get("start_ms").getAsLong() : null,
                        segment.has("end_ms") && !segment.get("end_ms").isJsonNull() ? segment.get("end_ms").getAsLong() : null
                ));
            }
        }

        return new TranscriptionProviderResult(provider, model, text, sentenceText, durationMs, confidence, segments);
    }

    private String getString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static final class ProviderThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "VoicePunish-LocalFunAsr");
            thread.setDaemon(true);
            return thread;
        }
    }
}
