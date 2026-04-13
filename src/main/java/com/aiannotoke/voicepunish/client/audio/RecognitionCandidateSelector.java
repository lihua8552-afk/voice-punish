package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.util.TextRepairUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecognitionCandidateSelector {

    private RecognitionCandidateSelector() {
    }

    public static RecognitionResult selectCandidates(
            List<TranscriptCandidate> rawCandidates,
            RecognitionHintSet hintSet,
            List<String> partialHistory,
            String lastSentNormalized
    ) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return null;
        }

        Map<String, TranscriptCandidate> bestByNormalized = new LinkedHashMap<>();
        for (TranscriptCandidate rawCandidate : rawCandidates) {
            String prepared = RecognitionPostProcessor.prepareCandidateText(rawCandidate.transcript(), hintSet);
            String normalized = RecognitionPostProcessor.normalizeForScoring(prepared, hintSet);
            if (RecognitionPostProcessor.isLikelyNoise(prepared, normalized, hintSet)) {
                continue;
            }
            if (lastSentNormalized != null && !lastSentNormalized.isBlank() && lastSentNormalized.equals(normalized)) {
                continue;
            }

            int matchedHints = hintSet.countMatches(normalized);
            double score = scoreCandidate(rawCandidate.source(), prepared, normalized, rawCandidate.confidence(), matchedHints, partialHistory, lastSentNormalized);
            TranscriptCandidate scoredCandidate = new TranscriptCandidate(
                    rawCandidate.source(),
                    prepared,
                    normalized,
                    rawCandidate.confidence(),
                    matchedHints,
                    score
            );

            TranscriptCandidate existing = bestByNormalized.get(normalized);
            if (existing == null || scoredCandidate.score() > existing.score()) {
                bestByNormalized.put(normalized, scoredCandidate);
            }
        }

        if (bestByNormalized.isEmpty()) {
            return null;
        }

        List<TranscriptCandidate> rankedCandidates = new ArrayList<>(bestByNormalized.values());
        rankedCandidates.sort(Comparator.comparingDouble(TranscriptCandidate::score).reversed());
        return new RecognitionResult(rankedCandidates.get(0).transcript(), rankedCandidates.get(0), List.copyOf(rankedCandidates));
    }

    private static double scoreCandidate(
            TranscriptCandidate.Source source,
            String preparedText,
            String normalizedText,
            double confidence,
            int matchedHints,
            List<String> partialHistory,
            String lastSentNormalized
    ) {
        double score = switch (source) {
            case MAIN_FLUSH_FINAL -> 26D;
            case MAIN_FINAL -> 22D;
            case MAIN_PARTIAL -> 16D;
            case HINT_FINAL -> 18D;
            case MAIN_ALTERNATIVE -> 14D;
            case SHRIEK_FALLBACK -> 10D;
        };

        score += Math.max(0D, Math.min(1D, confidence)) * 12D;
        score += RecognitionPostProcessor.chineseRatio(preparedText) * 18D;
        score += Math.min(5, matchedHints) * 8D;

        if (TextRepairUtil.looksLikeMojibake(preparedText)) {
            score -= 40D;
        }
        if (RecognitionPostProcessor.containsChinese(preparedText) && preparedText.length() >= 2) {
            score += 6D;
        }

        for (String partial : partialHistory) {
            if (partial == null || partial.isBlank()) {
                continue;
            }
            if (normalizedText.contains(partial) || partial.contains(normalizedText)) {
                score += 4D;
                break;
            }
        }

        if (!RecognitionPostProcessor.containsChinese(preparedText) && matchedHints == 0) {
            score -= 8D;
        }
        return score;
    }
}
