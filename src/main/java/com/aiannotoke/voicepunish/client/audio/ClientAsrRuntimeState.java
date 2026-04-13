package com.aiannotoke.voicepunish.client.audio;

public final class ClientAsrRuntimeState {

    public enum Status {
        DISABLED,
        PREPARING,
        STARTING,
        READY,
        ERROR
    }

    private static volatile Status status = Status.DISABLED;
    private static volatile String detail = "";
    private static volatile long updatedAt = System.currentTimeMillis();
    private static volatile long version;

    private ClientAsrRuntimeState() {
    }

    public static void setDisabled(String detailMessage) {
        update(Status.DISABLED, detailMessage);
    }

    public static void setPreparing(String detailMessage) {
        update(Status.PREPARING, detailMessage);
    }

    public static void setStarting(String detailMessage) {
        update(Status.STARTING, detailMessage);
    }

    public static void setReady(String detailMessage) {
        update(Status.READY, detailMessage);
    }

    public static void setError(String detailMessage) {
        update(Status.ERROR, detailMessage);
    }

    public static Snapshot snapshot() {
        return new Snapshot(status, detail, updatedAt, version);
    }

    private static synchronized void update(Status nextStatus, String nextDetail) {
        String sanitized = nextDetail == null ? "" : nextDetail.trim();
        if (status == nextStatus && detail.equals(sanitized)) {
            return;
        }
        status = nextStatus;
        detail = sanitized;
        updatedAt = System.currentTimeMillis();
        version++;
    }

    public record Snapshot(Status status, String detail, long updatedAt, long version) {

        public boolean shouldRender(long now) {
            return status != Status.DISABLED && (status != Status.READY || now - updatedAt <= 8_000L);
        }

        public int color() {
            return switch (status) {
                case READY -> 0xFF55FFAA;
                case ERROR -> 0xFFFF7A7A;
                case PREPARING -> 0xFFFFD166;
                case STARTING -> 0xFF7CC7FF;
                case DISABLED -> 0xFF909090;
            };
        }

        public String line() {
            return switch (status) {
                case DISABLED -> "ASR: off";
                case PREPARING -> detail.isEmpty() ? "ASR: preparing offline runtime" : "ASR: " + detail;
                case STARTING -> detail.isEmpty() ? "ASR: starting local service" : "ASR: " + detail;
                case READY -> detail.isEmpty() ? "ASR: ready" : "ASR: " + detail;
                case ERROR -> detail.isEmpty() ? "ASR: failed" : "ASR: " + detail;
            };
        }
    }
}
