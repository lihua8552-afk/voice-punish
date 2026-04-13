package com.aiannotoke.voicepunish.client.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public final class VoskRecognitionParser {

    private VoskRecognitionParser() {
    }

    public static ParsedResult parseResult(String json) {
        if (json == null || json.isBlank()) {
            return ParsedResult.EMPTY;
        }

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String text = getString(object, "text");
            List<Alternative> alternatives = new ArrayList<>();
            if (object.has("alternatives") && object.get("alternatives").isJsonArray()) {
                JsonArray array = object.getAsJsonArray("alternatives");
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject alternative = element.getAsJsonObject();
                    String alternativeText = getString(alternative, "text");
                    if (alternativeText.isBlank()) {
                        continue;
                    }
                    double confidence = alternative.has("confidence") ? alternative.get("confidence").getAsDouble() : 0D;
                    alternatives.add(new Alternative(alternativeText, confidence));
                }
            }
            return new ParsedResult(text, List.copyOf(alternatives));
        } catch (Exception ignored) {
            return ParsedResult.EMPTY;
        }
    }

    public static String parsePartial(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return getString(object, "partial");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String getString(JsonObject object, String property) {
        if (!object.has(property)) {
            return "";
        }
        JsonElement element = object.get(property);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    public record ParsedResult(String text, List<Alternative> alternatives) {
        private static final ParsedResult EMPTY = new ParsedResult("", List.of());
    }

    public record Alternative(String text, double confidence) {
    }
}
