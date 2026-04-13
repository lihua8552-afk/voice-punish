package com.aiannotoke.voicepunish.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public final class MicrophoneVolumeMonitor {

    private static final AudioFormat FORMAT = new AudioFormat(16000F, 16, 1, true, false);

    private volatile float latestLevel;
    private volatile boolean running;
    private volatile boolean started;
    private Thread thread;
    private TargetDataLine line;

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        running = true;
        thread = new Thread(this::runLoop, "VoicePunish-MicMonitor");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        started = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        closeLine();
    }

    public float getLatestLevel() {
        return latestLevel;
    }

    private void runLoop() {
        byte[] buffer = new byte[3200];

        while (running) {
            try {
                ensureLine();
                if (line == null) {
                    Thread.sleep(1000L);
                    continue;
                }

                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) {
                    latestLevel = computeLevel(buffer, read);
                } else {
                    latestLevel = 0F;
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
                latestLevel = 0F;
                closeLine();
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void ensureLine() {
        if (line != null && line.isOpen()) {
            return;
        }

        try {
            line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
            line.open(FORMAT);
            line.start();
        } catch (Exception ignored) {
            closeLine();
        }
    }

    private void closeLine() {
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    private float computeLevel(byte[] buffer, int read) {
        int samples = read / 2;
        if (samples <= 0) {
            return 0F;
        }

        double squareSum = 0D;
        for (int i = 0; i + 1 < read; i += 2) {
            int low = buffer[i] & 0xFF;
            int high = buffer[i + 1];
            short sample = (short) ((high << 8) | low);
            double normalized = sample / 32768D;
            squareSum += normalized * normalized;
        }
        return (float) Math.min(1D, Math.sqrt(squareSum / samples));
    }
}
