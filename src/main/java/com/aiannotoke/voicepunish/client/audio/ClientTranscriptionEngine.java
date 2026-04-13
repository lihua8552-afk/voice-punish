package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishClientConfig;
import com.aiannotoke.voicepunish.moderation.TextNormalizer;
import com.aiannotoke.voicepunish.util.TextRepairUtil;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientTranscriptionEngine {

    static final long INIT_RETRY_MS = 10_000L;
    static final long AUTO_FINALIZE_DELAY_MS = 120L;
    static final long FALLBACK_MATCH_WINDOW_MS = 2_000L;
    static final long DUPLICATE_WINDOW_MS = 1_500L;
    static final long SPEECH_ACTIVITY_TIMEOUT_MS = 450L;
    static final long PARTIAL_STALL_TIMEOUT_MS = 300L;
    static final long PROVIDER_HEALTHY_CHECK_INTERVAL_MS = 15_000L;
    static final long PROVIDER_UNHEALTHY_CHECK_INTERVAL_MS = 1_000L;
    static final double SPEECH_ACTIVITY_THRESHOLD = 0.045D;

    private final VoicePunishClientConfig config;
    private final Path safeModelRoot;
    private final Path shriekModelRoot;
    private final Deque<String> outboundTranscripts = new ArrayDeque<>();
    private final Deque<String> partialHistory = new ArrayDeque<>();
    private final List<String> mainSegments = new ArrayList<>();
    private final List<VoskRecognitionParser.Alternative> latestMainAlternatives = new ArrayList<>();
    private final List<String> hintSegments = new ArrayList<>();
    private final ByteArrayOutputStream utterancePcmBuffer = new ByteArrayOutputStream();
    private final TranscriptionProvider transcriptionProvider;

    private RecognitionHintSet hintSet;

    private Model mainModel;
    private Model hintModel;
    private Recognizer mainRecognizer;
    private Recognizer hintRecognizer;

    private String activeMainModelName = "";
    private String activeHintModelName = "";
    private long nextInitRetryAt;
    private long pendingFinalizeAt = -1L;
    private boolean previousRecording;

    private String currentMainPartial = "";
    private String currentHintPartial = "";
    private String latestFallbackTranscript = "";
    private String latestFallbackNormalized = "";
    private long latestFallbackAt = Long.MIN_VALUE;
    private String lastSentNormalized = "";
    private long lastSentAt = Long.MIN_VALUE;
    private long lastAudioFrameAt = Long.MIN_VALUE;
    private long lastSpeechLikeAudioAt = Long.MIN_VALUE;
    private long lastMainPartialAt = Long.MIN_VALUE;
    private long lastMainGrowthAt = Long.MIN_VALUE;
    private long lastRecognitionEvidenceAt = Long.MIN_VALUE;
    private long lastProviderHealthCheckAt = Long.MIN_VALUE;
    private long utteranceStartedAt = Long.MIN_VALUE;
    private long utteranceLastAudioAt = Long.MIN_VALUE;
    private boolean hasPendingTranscriptEvidence;
    private boolean segmentStarted;
    private CompletableFuture<Void> providerRequestChain = CompletableFuture.completedFuture(null);
    private CompletableFuture<Boolean> providerHealthRefresh = CompletableFuture.completedFuture(false);

    public ClientTranscriptionEngine(VoicePunishClientConfig config, Path safeModelRoot, Path shriekModelRoot) {
        this.config = config.copy();
        this.config.fillDefaults();
        this.safeModelRoot = safeModelRoot;
        this.shriekModelRoot = shriekModelRoot;
        this.hintSet = RecognitionHintSet.compile(List.of(), this.config.customHotWords);
        this.transcriptionProvider = new LocalFunAsrProvider(this.config);
    }

    public synchronized void updateServerHints(List<String> serverHints) {
        hintSet = RecognitionHintSet.compile(serverHints, config.customHotWords);
        if (hintRecognizer != null) {
            rebuildHintRecognizer("server_hints_updated");
        }
        if (config.debugRecognition) {
            VoicePunishMod.LOGGER.info("[Transcription] Updated hint set with {} terms", hintSet.hints().size());
        }
    }

    public synchronized void onAudioFrame(byte[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        lastAudioFrameAt = now;
        double speechActivity = computeSpeechActivity(pcm);
        if (speechActivity >= SPEECH_ACTIVITY_THRESHOLD) {
            lastSpeechLikeAudioAt = now;
            markSegmentStarted("audio_activity");
        }

        tryInitializeIfNeeded(now);
        if (mainRecognizer == null) {
            return;
        }

        processMainRecognizer(pcm, now);
        processHintRecognizer(pcm, now);
        captureUtteranceAudio(pcm, now, speechActivity);
    }

    public synchronized void acceptFallbackTranscript(String transcript) {
        String repaired = TextRepairUtil.repairIfNeeded(transcript);
        if (repaired != null) {
            repaired = repaired.trim();
        }
        if (repaired == null || repaired.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (mainRecognizer == null) {
            if (enqueueIfFresh(repaired)) {
                logSendingTranscript("fallback_direct", repaired);
            }
            return;
        }

        latestFallbackTranscript = repaired;
        latestFallbackNormalized = TextNormalizer.normalizePlain(RecognitionPostProcessor.prepareCandidateText(repaired, hintSet));
        latestFallbackAt = now;
        lastRecognitionEvidenceAt = now;
        hasPendingTranscriptEvidence = true;
        markSegmentStarted("fallback");
        if (config.debugRecognition) {
            VoicePunishMod.LOGGER.info("[Transcription] fallback_received text={}", repaired);
        }
    }

    public synchronized void onClientTick(boolean recording) {
        long now = System.currentTimeMillis();
        tryInitializeIfNeeded(now);
        refreshProviderHealthIfNeeded(now);

        if (previousRecording && !recording) {
            finalizeCurrentUtterance("recording_stopped");
        } else if (pendingFinalizeAt > 0L && now >= pendingFinalizeAt && currentMainPartial.isBlank()) {
            finalizeCurrentUtterance("main_final");
        } else {
            String idleReason = decideIdleFinalizeReason(
                    now,
                    hasPendingTranscriptEvidence,
                    lastAudioFrameAt,
                    lastSpeechLikeAudioAt,
                    lastMainPartialAt,
                    lastMainGrowthAt,
                    lastRecognitionEvidenceAt,
                    latestFallbackAt,
                    latestFallbackNormalized
            );
            if (idleReason != null) {
                finalizeCurrentUtterance(idleReason);
            }
        }

        previousRecording = recording;
    }

    public synchronized String pollReadyTranscript() {
        return outboundTranscripts.pollFirst();
    }

    public synchronized void close() {
        resetUtteranceState();
        closeRecognizer(mainRecognizer);
        closeRecognizer(hintRecognizer);
        closeModel(mainModel);
        closeModel(hintModel);
        transcriptionProvider.close();
        mainRecognizer = null;
        hintRecognizer = null;
        mainModel = null;
        hintModel = null;
    }

    private void processMainRecognizer(byte[] pcm, long now) {
        boolean acceptedFinal = mainRecognizer.acceptWaveForm(pcm, pcm.length);
        if (acceptedFinal) {
            VoskRecognitionParser.ParsedResult result = VoskRecognitionParser.parseResult(mainRecognizer.getResult());
            recordMainResult(result, now);
            currentMainPartial = "";
            pendingFinalizeAt = now + AUTO_FINALIZE_DELAY_MS;
            return;
        }

        String partial = RecognitionPostProcessor.normalizeForScoring(
                VoskRecognitionParser.parsePartial(mainRecognizer.getPartialResult()),
                hintSet
        );
        if (partial.isBlank()) {
            currentMainPartial = "";
            return;
        }

        lastMainPartialAt = now;
        lastRecognitionEvidenceAt = now;
        hasPendingTranscriptEvidence = true;
        markSegmentStarted("partial");
        if (!partial.equals(currentMainPartial)) {
            currentMainPartial = partial;
            lastMainGrowthAt = now;
            rememberPartial(currentMainPartial);
            if (config.debugRecognition) {
                VoicePunishMod.LOGGER.info("[Transcription] partial_growth text={}", currentMainPartial);
            }
        }
    }

    private void processHintRecognizer(byte[] pcm, long now) {
        if (hintRecognizer == null) {
            return;
        }

        boolean acceptedFinal = hintRecognizer.acceptWaveForm(pcm, pcm.length);
        if (acceptedFinal) {
            VoskRecognitionParser.ParsedResult result = VoskRecognitionParser.parseResult(hintRecognizer.getResult());
            if (!result.text().isBlank()) {
                hintSegments.add(result.text());
                hasPendingTranscriptEvidence = true;
                lastRecognitionEvidenceAt = now;
                lastMainGrowthAt = now;
                markSegmentStarted("hint_final");
            }
            currentHintPartial = "";
            return;
        }

        currentHintPartial = VoskRecognitionParser.parsePartial(hintRecognizer.getPartialResult());
    }

    private void recordMainResult(VoskRecognitionParser.ParsedResult result, long now) {
        if (!result.text().isBlank()) {
            mainSegments.add(result.text());
            hasPendingTranscriptEvidence = true;
            lastMainGrowthAt = now;
            lastMainPartialAt = now;
            lastRecognitionEvidenceAt = now;
            markSegmentStarted("main_final_segment");
        }
        if (!result.alternatives().isEmpty()) {
            hasPendingTranscriptEvidence = true;
            lastRecognitionEvidenceAt = now;
            markSegmentStarted("main_alternatives");
        }
        latestMainAlternatives.clear();
        latestMainAlternatives.addAll(result.alternatives());
    }

    private void captureUtteranceAudio(byte[] pcm, long now, double speechActivity) {
        boolean shouldCapture = segmentStarted
                || speechActivity >= SPEECH_ACTIVITY_THRESHOLD
                || hasPendingTranscriptEvidence
                || !currentMainPartial.isBlank();
        if (!shouldCapture) {
            return;
        }
        if (utteranceStartedAt <= Long.MIN_VALUE) {
            utteranceStartedAt = now;
        }
        utteranceLastAudioAt = now;
        utterancePcmBuffer.writeBytes(pcm);
    }

    private void finalizeCurrentUtterance(String reason) {
        byte[] utterancePcm = utterancePcmBuffer.toByteArray();
        if (!hasPendingTranscriptEvidence && latestFallbackTranscript.isBlank() && currentMainPartial.isBlank()) {
            resetUtteranceState();
            return;
        }

        List<TranscriptCandidate> candidates = new ArrayList<>();

        String mainCombined = joinSegments(mainSegments);
        if (!mainCombined.isBlank()) {
            candidates.add(new TranscriptCandidate(
                    TranscriptCandidate.Source.MAIN_FINAL,
                    mainCombined,
                    "",
                    1D,
                    0,
                    0D
            ));
            String mainAlternativePrefix = joinSegments(mainSegments.size() <= 1 ? List.of() : mainSegments.subList(0, mainSegments.size() - 1));
            for (VoskRecognitionParser.Alternative alternative : latestMainAlternatives) {
                String alternativeText = joinSegments(List.of(mainAlternativePrefix, alternative.text()));
                if (!alternativeText.isBlank()) {
                    candidates.add(new TranscriptCandidate(
                            TranscriptCandidate.Source.MAIN_ALTERNATIVE,
                            alternativeText,
                            "",
                            alternative.confidence(),
                            0,
                            0D
                    ));
                }
            }
        }

        if (!currentMainPartial.isBlank()) {
            candidates.add(new TranscriptCandidate(
                    TranscriptCandidate.Source.MAIN_PARTIAL,
                    currentMainPartial,
                    "",
                    0.65D,
                    0,
                    0D
            ));
        }

        if (mainRecognizer != null) {
            VoskRecognitionParser.ParsedResult flushResult = VoskRecognitionParser.parseResult(mainRecognizer.getFinalResult());
            String mainFlushCombined = joinSegments(List.of(mainCombined, flushResult.text()));
            if (!mainFlushCombined.isBlank()) {
                candidates.add(new TranscriptCandidate(
                        TranscriptCandidate.Source.MAIN_FLUSH_FINAL,
                        mainFlushCombined,
                        "",
                        1D,
                        0,
                        0D
                ));
            }
        }

        if (hintRecognizer != null) {
            VoskRecognitionParser.ParsedResult hintFlushResult = VoskRecognitionParser.parseResult(hintRecognizer.getFinalResult());
            String hintCombined = joinSegments(List.of(joinSegments(hintSegments), hintFlushResult.text()));
            if (!hintCombined.isBlank()) {
                candidates.add(new TranscriptCandidate(
                        TranscriptCandidate.Source.HINT_FINAL,
                        hintCombined,
                        "",
                        0.9D,
                        0,
                        0D
                ));
            }
        }

        long now = System.currentTimeMillis();
        if (!latestFallbackTranscript.isBlank() && now - latestFallbackAt <= FALLBACK_MATCH_WINDOW_MS) {
            candidates.add(new TranscriptCandidate(
                    TranscriptCandidate.Source.SHRIEK_FALLBACK,
                    latestFallbackTranscript,
                    "",
                    0.75D,
                    0,
                    0D
            ));
        }

        RecognitionResult localResult = RecognitionCandidateSelector.selectCandidates(
                candidates,
                hintSet,
                List.copyOf(partialHistory),
                lastSentNormalized
        );
        String localFallbackTranscript = localResult == null ? "" : localResult.finalTranscript();

        if (shouldUseProvider(transcriptionProvider, utterancePcm)) {
            submitProviderTranscription(reason, utterancePcm, localFallbackTranscript);
        } else if (!localFallbackTranscript.isBlank() && enqueueIfFresh(localFallbackTranscript)) {
            logFinalResult(reason, "vosk_fallback", localFallbackTranscript, localResult);
            logSendingTranscript(reason, localFallbackTranscript);
        }

        if (mainRecognizer != null) {
            mainRecognizer.reset();
        }
        if (hintRecognizer != null) {
            hintRecognizer.reset();
        }
        resetUtteranceState();
    }

    private void submitProviderTranscription(String reason, byte[] utterancePcm, String localFallbackTranscript) {
        byte[] wavBytes = WavAudioUtil.wrapPcmAsWav(utterancePcm);
        long durationMs = WavAudioUtil.pcmDurationMs(utterancePcm);

        providerRequestChain = providerRequestChain
                .exceptionally(throwable -> null)
                .thenCompose(ignored -> transcriptionProvider.transcribeWavAsync(wavBytes, durationMs)
                        .handle((providerResult, throwable) -> {
                            synchronized (this) {
                                String selectedTranscript = "";
                                String selectedProvider = "local_funasr";
                                if (throwable == null && providerResult != null) {
                                    selectedTranscript = RecognitionPostProcessor.prepareCandidateText(
                                            providerResult.preferredText(config.preferSentenceText),
                                            hintSet
                                    );
                                    selectedProvider = providerResult.provider().isBlank() ? "local_funasr" : providerResult.provider();
                                }

                                if ((selectedTranscript == null || selectedTranscript.isBlank()) && config.allowProviderFallback) {
                                    selectedTranscript = localFallbackTranscript;
                                    selectedProvider = "vosk_fallback";
                                }

                                if (selectedTranscript != null && !selectedTranscript.isBlank() && enqueueIfFresh(selectedTranscript)) {
                                    if (config.debugRecognition) {
                                        VoicePunishMod.LOGGER.info(
                                                "[Transcription] provider_result reason={} provider={} durationMs={} text={}",
                                                reason,
                                                selectedProvider,
                                                durationMs,
                                                selectedTranscript
                                        );
                                    }
                                    logSendingTranscript(reason + ":" + selectedProvider, selectedTranscript);
                                }
                            }
                            return null;
                        }));
    }

    private boolean enqueueIfFresh(String transcript) {
        String prepared = RecognitionPostProcessor.prepareCandidateText(transcript, hintSet);
        String normalized = TextNormalizer.normalizePlain(prepared);
        if (RecognitionPostProcessor.isLikelyNoise(prepared, normalized, hintSet)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (!lastSentNormalized.isBlank() && lastSentNormalized.equals(normalized) && now - lastSentAt <= DUPLICATE_WINDOW_MS) {
            return false;
        }

        outboundTranscripts.addLast(prepared);
        lastSentNormalized = normalized;
        lastSentAt = now;
        return true;
    }

    private void tryInitializeIfNeeded(long now) {
        if (mainRecognizer != null && (!config.enableHintRecognizer || hintRecognizer != null)) {
            return;
        }
        if (now < nextInitRetryAt) {
            return;
        }

        try {
            Path preferredModelPath = findModel(config.preferredModelName);
            Path fallbackModelPath = findModel(config.fallbackModelName);

            Path selectedMainModel = preferredModelPath != null ? preferredModelPath : fallbackModelPath;
            if (selectedMainModel == null) {
                nextInitRetryAt = now + INIT_RETRY_MS;
                VoicePunishMod.LOGGER.warn("No Vosk model available yet for transcription engine. Waiting for Shriek fallback model download.");
                return;
            }

            if (mainRecognizer == null) {
                closeRecognizer(mainRecognizer);
                closeModel(mainModel);
                mainModel = new Model(selectedMainModel.toString());
                mainRecognizer = new Recognizer(mainModel, com.pryzmm.ShriekConstants.sampleRate);
                mainRecognizer.setMaxAlternatives(config.maxAlternatives);
                mainRecognizer.setWords(true);
                mainRecognizer.setPartialWords(true);
                activeMainModelName = selectedMainModel.getFileName().toString();
            }

            String hintReason = "disabled";
            if (config.enableHintRecognizer) {
                if (hintRecognizer == null && fallbackModelPath != null) {
                    closeModel(hintModel);
                    hintModel = new Model(fallbackModelPath.toString());
                    hintRecognizer = createHintRecognizer(hintModel);
                    activeHintModelName = fallbackModelPath.getFileName().toString();
                    hintReason = hintRecognizer != null ? "enabled" : "fallback to disabled";
                } else if (fallbackModelPath == null) {
                    hintReason = "fallback model missing";
                } else {
                    hintReason = "enabled";
                }
            }

            String mainReason = preferredModelPath != null
                    ? "preferred model found"
                    : "preferred model missing, using fallback model";
            VoicePunishMod.LOGGER.info(
                    "Voice transcription initialized: provider={} mainModel={} ({}) | hintRecognizer={} ({})",
                    config.transcriptionProvider,
                    activeMainModelName,
                    mainReason,
                    config.enableHintRecognizer,
                    hintReason
            );
        } catch (Exception exception) {
            nextInitRetryAt = now + INIT_RETRY_MS;
            VoicePunishMod.LOGGER.error("Failed to initialize voice transcription engine, will retry", exception);
            close();
        }
    }

    private void refreshProviderHealthIfNeeded(long now) {
        if (!transcriptionProvider.isConfigured()) {
            return;
        }
        if (!providerHealthRefresh.isDone()) {
            return;
        }
        long interval = transcriptionProvider.isAvailable()
                ? PROVIDER_HEALTHY_CHECK_INTERVAL_MS
                : PROVIDER_UNHEALTHY_CHECK_INTERVAL_MS;
        if (lastProviderHealthCheckAt > Long.MIN_VALUE && now - lastProviderHealthCheckAt < interval) {
            return;
        }
        lastProviderHealthCheckAt = now;
        providerHealthRefresh = transcriptionProvider.refreshHealthAsync();
        providerHealthRefresh.thenAccept(healthy -> {
            if (config.debugRecognition) {
                VoicePunishMod.LOGGER.info("[Transcription] provider_health provider={} healthy={}", transcriptionProvider.providerId(), healthy);
            }
        });
    }

    private Path findModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }

        Path safeCandidate = safeModelRoot.resolve(modelName);
        if (hasUsableModel(safeCandidate)) {
            return safeCandidate;
        }

        Path shriekCandidate = shriekModelRoot.resolve(modelName);
        return hasUsableModel(shriekCandidate) ? shriekCandidate : null;
    }

    private boolean hasUsableModel(Path path) {
        return path != null && Files.exists(path.resolve("am").resolve("final.mdl"));
    }

    private void rememberPartial(String partial) {
        if (partial == null || partial.isBlank()) {
            return;
        }
        if (!partialHistory.isEmpty() && partial.equals(partialHistory.peekLast())) {
            return;
        }
        partialHistory.addLast(partial);
        while (partialHistory.size() > 6) {
            partialHistory.removeFirst();
        }
    }

    private void markSegmentStarted(String reason) {
        if (segmentStarted) {
            return;
        }
        segmentStarted = true;
        if (config.debugRecognition) {
            VoicePunishMod.LOGGER.info("[Transcription] segment_started reason={}", reason);
        }
    }

    private void logFinalResult(String reason, String provider, String transcript, RecognitionResult result) {
        if (!config.debugRecognition) {
            return;
        }
        int candidateCount = result == null ? 0 : result.rankedCandidates().size();
        VoicePunishMod.LOGGER.info(
                "[Transcription] finalizing {} -> provider={} selected={} candidates={}",
                reason,
                provider,
                transcript,
                candidateCount
        );
    }

    private void logSendingTranscript(String reason, String transcript) {
        if (config.debugRecognition) {
            VoicePunishMod.LOGGER.info("[Transcription] sending transcript reason={} text={}", reason, transcript);
        }
    }

    private String joinSegments(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(segment.trim());
        }
        return builder.toString().trim();
    }

    private void resetUtteranceState() {
        mainSegments.clear();
        latestMainAlternatives.clear();
        hintSegments.clear();
        partialHistory.clear();
        utterancePcmBuffer.reset();
        utteranceStartedAt = Long.MIN_VALUE;
        utteranceLastAudioAt = Long.MIN_VALUE;
        currentMainPartial = "";
        currentHintPartial = "";
        latestFallbackTranscript = "";
        latestFallbackNormalized = "";
        latestFallbackAt = Long.MIN_VALUE;
        pendingFinalizeAt = -1L;
        lastAudioFrameAt = Long.MIN_VALUE;
        lastSpeechLikeAudioAt = Long.MIN_VALUE;
        lastMainPartialAt = Long.MIN_VALUE;
        lastMainGrowthAt = Long.MIN_VALUE;
        lastRecognitionEvidenceAt = Long.MIN_VALUE;
        hasPendingTranscriptEvidence = false;
        segmentStarted = false;
    }

    private void closeRecognizer(Recognizer recognizer) {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void rebuildHintRecognizer(String reason) {
        if (!config.enableHintRecognizer || hintModel == null) {
            return;
        }

        Recognizer previous = hintRecognizer;
        try {
            hintRecognizer = createHintRecognizer(hintModel);
            if (config.debugRecognition) {
                VoicePunishMod.LOGGER.info(
                        "[Transcription] rebuilt hint recognizer reason={} enabled={}",
                        reason,
                        hintRecognizer != null
                );
            }
        } finally {
            if (previous != hintRecognizer) {
                closeRecognizer(previous);
            }
        }
    }

    private Recognizer createHintRecognizer(Model model) {
        if (model == null) {
            return null;
        }

        try {
            Recognizer grammarRecognizer = new Recognizer(model, com.pryzmm.ShriekConstants.sampleRate, hintSet.grammarJson());
            grammarRecognizer.setWords(true);
            grammarRecognizer.setPartialWords(true);
            return grammarRecognizer;
        } catch (Throwable throwable) {
            VoicePunishMod.LOGGER.warn("Failed to create grammar hint recognizer, falling back to plain small-model recognizer", throwable);
        }

        try {
            Recognizer plainRecognizer = new Recognizer(model, com.pryzmm.ShriekConstants.sampleRate);
            plainRecognizer.setWords(true);
            plainRecognizer.setPartialWords(true);
            return plainRecognizer;
        } catch (Throwable throwable) {
            VoicePunishMod.LOGGER.error("Failed to create fallback hint recognizer", throwable);
            return null;
        }
    }

    private void closeModel(Model model) {
        if (model != null) {
            try {
                model.close();
            } catch (Exception ignored) {
            }
        }
    }

    static boolean shouldUseProvider(TranscriptionProvider transcriptionProvider, byte[] utterancePcm) {
        return transcriptionProvider != null
                && transcriptionProvider.isConfigured()
                && transcriptionProvider.isAvailable()
                && utterancePcm != null
                && utterancePcm.length > 0;
    }

    static double computeSpeechActivity(byte[] pcm) {
        if (pcm == null || pcm.length < 2) {
            return 0D;
        }

        int samples = pcm.length / 2;
        double squareSum = 0D;
        double peak = 0D;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int low = pcm[i] & 0xFF;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            double normalized = sample / 32768D;
            squareSum += normalized * normalized;
            peak = Math.max(peak, Math.abs(normalized));
        }
        double rms = Math.sqrt(squareSum / samples);
        return Math.min(1D, Math.max(rms * 6D, peak));
    }

    static String decideIdleFinalizeReason(
            long now,
            boolean hasPendingTranscriptEvidence,
            long lastAudioFrameAt,
            long lastSpeechLikeAudioAt,
            long lastMainPartialAt,
            long lastMainGrowthAt,
            long lastRecognitionEvidenceAt,
            long latestFallbackAt,
            String latestFallbackNormalized
    ) {
        if (!hasPendingTranscriptEvidence) {
            return null;
        }

        long latestRecognitionAt = Math.max(
                Math.max(lastMainPartialAt, lastMainGrowthAt),
                Math.max(lastRecognitionEvidenceAt, latestFallbackAt)
        );

        boolean speechTimedOut;
        if (lastSpeechLikeAudioAt > Long.MIN_VALUE) {
            speechTimedOut = now - lastSpeechLikeAudioAt >= SPEECH_ACTIVITY_TIMEOUT_MS;
        } else {
            speechTimedOut = latestRecognitionAt > Long.MIN_VALUE && now - latestRecognitionAt >= SPEECH_ACTIVITY_TIMEOUT_MS;
        }

        if (!speechTimedOut) {
            return null;
        }

        boolean recognitionStalled = latestRecognitionAt > Long.MIN_VALUE
                && now - latestRecognitionAt >= PARTIAL_STALL_TIMEOUT_MS;
        boolean fallbackStalled = latestFallbackAt > Long.MIN_VALUE
                && !latestFallbackNormalized.isBlank()
                && now - latestFallbackAt >= PARTIAL_STALL_TIMEOUT_MS
                && (lastMainGrowthAt <= Long.MIN_VALUE || latestFallbackAt >= lastMainGrowthAt);

        if (fallbackStalled) {
            return "fallback_stall_timeout";
        }
        if (recognitionStalled || lastAudioFrameAt > Long.MIN_VALUE) {
            return "silence_timeout";
        }
        return null;
    }
}
