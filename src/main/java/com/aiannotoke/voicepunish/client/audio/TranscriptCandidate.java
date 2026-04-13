package com.aiannotoke.voicepunish.client.audio;

public record TranscriptCandidate(
        Source source,
        String transcript,
        String normalizedTranscript,
        double confidence,
        int matchedHints,
        double score
) {

    public TranscriptCandidate withScore(int matchedHints, double score) {
        return new TranscriptCandidate(source, transcript, normalizedTranscript, confidence, matchedHints, score);
    }

    public enum Source {
        MAIN_FINAL,
        MAIN_ALTERNATIVE,
        MAIN_PARTIAL,
        MAIN_FLUSH_FINAL,
        HINT_FINAL,
        SHRIEK_FALLBACK
    }
}
