package com.aiannotoke.voicepunish.client.hud;

import com.aiannotoke.voicepunish.client.audio.ClientAsrRuntimeState;
import com.aiannotoke.voicepunish.client.audio.ClientVoiceRuntimeState;
import com.aiannotoke.voicepunish.network.VoiceHudPayload;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Locale;

public final class VoiceHudState {

    private static final long TRANSCRIPT_LIFETIME_MS = 5000L;
    private String transcript = "";
    private long transcriptExpiresAt;

    public void accept(VoiceHudPayload payload) {
        long now = System.currentTimeMillis();

        if (payload.hasTranscript()) {
            transcript = payload.transcript();
            transcriptExpiresAt = now + TRANSCRIPT_LIFETIME_MS;
        }
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        long now = System.currentTimeMillis();
        TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        boolean showTranscript = now <= transcriptExpiresAt && !transcript.isEmpty();
        ClientAsrRuntimeState.Snapshot asrSnapshot = ClientAsrRuntimeState.snapshot();
        boolean showAsrStatus = asrSnapshot.shouldRender(now);
        float microphonePercent = ClientVoiceRuntimeState.getLatestPercentOfThreshold();
        int barWidth = 182;
        int maxTranscriptWidth = 300;
        Text transcriptText = showTranscript ? buildTranscriptText(textRenderer, maxTranscriptWidth) : Text.empty();
        Text asrStatusText = showAsrStatus ? Text.literal(asrSnapshot.line()) : Text.empty();
        int transcriptWidth = showTranscript ? textRenderer.getWidth(transcriptText) : 0;
        int asrWidth = showAsrStatus ? textRenderer.getWidth(asrStatusText) : 0;
        int panelWidth = Math.max(barWidth, Math.max(transcriptWidth, asrWidth)) + 12;
        int panelHeight = 20 + (showTranscript ? 12 : 0) + (showAsrStatus ? 12 : 0);
        int x = (screenWidth - panelWidth) / 2;
        int y = screenHeight - panelHeight - 46;
        int contentX = x + 6;
        int contentY = y + 4;

        if (showTranscript) {
            context.drawTextWithShadow(textRenderer, transcriptText, contentX, contentY, 0xFFFFFFFF);
            contentY += 12;
        }
        if (showAsrStatus) {
            context.drawTextWithShadow(textRenderer, asrStatusText, contentX, contentY, asrSnapshot.color());
            contentY += 12;
        }

        String microphoneText = String.format(Locale.ROOT, "Microphone %.0f%%", microphonePercent);
        context.drawTextWithShadow(textRenderer, Text.literal(microphoneText), contentX, contentY, 0xFFE0E0E0);
        contentY += 10;
        drawBar(
                context,
                contentX,
                contentY,
                barWidth,
                Math.min(microphonePercent, 100F),
                microphonePercent >= 100F ? 0xFFFF7A7A : (microphonePercent >= 70F ? 0xFFFFD166 : 0xFF55FFAA)
        );
    }

    private Text buildTranscriptText(TextRenderer textRenderer, int maxTranscriptWidth) {
        String prefix = "Transcript -> ";
        int prefixWidth = textRenderer.getWidth(prefix);
        int availableTranscriptWidth = Math.max(40, maxTranscriptWidth - prefixWidth);
        String visibleTranscript = transcript;
        if (textRenderer.getWidth(visibleTranscript) > availableTranscriptWidth) {
            String trimmed = textRenderer.trimToWidth(visibleTranscript, Math.max(10, availableTranscriptWidth - textRenderer.getWidth("...")));
            visibleTranscript = trimmed + "...";
        }
        return Text.literal(prefix + visibleTranscript);
    }

    private void drawBar(DrawContext context, int x, int y, int width, float percent, int fillColor) {
        int height = 7;
        int clampedWidth = Math.max(0, Math.min(width, Math.round(Math.max(0F, Math.min(percent, 100F)) / 100F * width)));
        context.fill(x, y, x + width, y + height, 0x55333333);
        context.fill(x, y, x + clampedWidth, y + height, fillColor);
    }
}
