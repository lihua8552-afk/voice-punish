package com.aiannotoke.voicepunish.client.audio;

import com.aiannotoke.voicepunish.util.TextRepairUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.vosk.Recognizer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class VoskUtf8Bridge {

    private static final Charset[] JSON_CHARSETS = new Charset[]{
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
            StandardCharsets.ISO_8859_1
    };
    private static volatile boolean initialized;
    private static volatile Function resultFunction;

    private VoskUtf8Bridge() {
    }

    public static String getResultText(Recognizer recognizer) {
        if (recognizer == null) {
            return "";
        }

        try {
            Pointer jsonPointer = invokeResultPointer(recognizer);
            if (jsonPointer != null) {
                byte[] rawJson = readCString(jsonPointer);
                if (rawJson.length > 0) {
                    List<String> transcriptCandidates = new ArrayList<>();
                    for (Charset charset : JSON_CHARSETS) {
                        transcriptCandidates.add(extractText(new String(rawJson, charset)));
                    }
                    String directText = TextRepairUtil.pickBestCandidate(transcriptCandidates);
                    if (!directText.isBlank()) {
                        return directText;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return TextRepairUtil.repairIfNeeded(extractText(recognizer.getResult()));
    }

    private static Pointer invokeResultPointer(Recognizer recognizer) {
        Function function = getResultFunction();
        if (function == null) {
            return null;
        }
        return (Pointer) function.invoke(Pointer.class, new Object[]{recognizer.getPointer()});
    }

    private static Function getResultFunction() {
        if (initialized) {
            return resultFunction;
        }

        synchronized (VoskUtf8Bridge.class) {
            if (initialized) {
                return resultFunction;
            }
            resultFunction = loadFunction("libvosk");
            if (resultFunction == null) {
                resultFunction = loadFunction("vosk");
            }
            initialized = true;
            return resultFunction;
        }
    }

    private static Function loadFunction(String libraryName) {
        try {
            return NativeLibrary.getInstance(libraryName).getFunction("vosk_recognizer_result");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (!object.has("text")) {
                return "";
            }
            return object.get("text").getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static byte[] readCString(Pointer pointer) {
        int length = 0;
        while (length < 65_536) {
            if (pointer.getByte(length) == 0) {
                break;
            }
            length++;
        }
        return length <= 0 ? new byte[0] : pointer.getByteArray(0, length);
    }
}
