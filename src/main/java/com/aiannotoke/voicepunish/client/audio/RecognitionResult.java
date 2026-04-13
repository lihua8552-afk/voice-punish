package com.aiannotoke.voicepunish.client.audio;

import java.util.List;

public record RecognitionResult(
        String finalTranscript,
        TranscriptCandidate selectedCandidate,
        List<TranscriptCandidate> rankedCandidates
) {
}
