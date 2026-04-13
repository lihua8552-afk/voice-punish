package com.aiannotoke.voicepunish.client.audio;

import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.lang.reflect.Field;

public final class ShriekVolumeSampler {

    private static Field microphoneHandlerField;
    private static Field lineField;

    private ShriekVolumeSampler() {
    }

    public static float sampleLevel() {
        try {
            if (microphoneHandlerField == null) {
                Class<?> eventHandlerClass = Class.forName("com.pryzmm.client.event.EventHandler");
                microphoneHandlerField = eventHandlerClass.getDeclaredField("microphoneHandler");
                microphoneHandlerField.setAccessible(true);
            }

            Object microphoneHandler = microphoneHandlerField.get(null);
            if (microphoneHandler == null) {
                return 0F;
            }

            if (lineField == null) {
                lineField = microphoneHandler.getClass().getDeclaredField("line");
                lineField.setAccessible(true);
            }

            Object line = lineField.get(microphoneHandler);
            if (!(line instanceof TargetDataLine targetDataLine)) {
                return 0F;
            }

            float level = targetDataLine.getLevel();
            if (Float.isNaN(level) || level < 0F) {
                return 0F;
            }
            if (level > 1F) {
                return 1F;
            }
            return level;
        } catch (Throwable ignored) {
            return 0F;
        }
    }
}
