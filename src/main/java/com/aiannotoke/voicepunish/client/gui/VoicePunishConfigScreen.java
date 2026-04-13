package com.aiannotoke.voicepunish.client.gui;

import com.aiannotoke.voicepunish.client.audio.ClientVoiceRuntimeState;
import com.aiannotoke.voicepunish.config.VoicePunishSettingsSnapshot;
import com.aiannotoke.voicepunish.network.SaveConfigEditorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoicePunishConfigScreen extends Screen {

    private VoicePunishSettingsSnapshot currentSnapshot;
    private final String configPath;
    private final List<LabeledField> labeledFields;

    private boolean enableVolumeModeration;
    private boolean enableTranscriptModeration;
    private String badWordsDraft;

    private TextFieldWidget minimumVolumeThresholdField;
    private TextFieldWidget volumeMultiplierField;
    private TextFieldWidget overThresholdHoldField;
    private TextFieldWidget loudCooldownField;
    private TextFieldWidget loudDamageField;
    private TextFieldWidget loudEscalatedDamageField;
    private TextFieldWidget badWordDamageField;
    private TextFieldWidget badWordEscalatedDamageField;
    private TextFieldWidget defaultEventCountField;
    private TextFieldWidget escalatedEventCountField;
    private TextFieldWidget hostileMobWeightField;
    private TextFieldWidget inventoryLossWeightField;
    private TextFieldWidget negativeEffectWeightField;
    private TextFieldWidget teleportWeightField;

    private Text statusMessage = Text.empty();
    private int statusColor = 0xFFFF5555;

    public VoicePunishConfigScreen(VoicePunishSettingsSnapshot snapshot, String configPath) {
        super(Text.translatable("voicepunish.screen.settings.title"));
        this.currentSnapshot = snapshot;
        this.configPath = configPath;
        this.labeledFields = new ArrayList<>();
        this.enableVolumeModeration = snapshot.enableVolumeModeration();
        this.enableTranscriptModeration = snapshot.enableTranscriptModeration();
        this.badWordsDraft = String.join("\n", snapshot.badWords());
    }

    @Override
    protected void init() {
        clearChildren();
        labeledFields.clear();

        int columnWidth = 150;
        int gap = 20;
        int leftX = this.width / 2 - columnWidth - gap / 2;
        int rightX = this.width / 2 + gap / 2;
        int startY = 46;
        int rowHeight = 24;

        addDrawableChild(ButtonWidget.builder(toggleLabel(Text.translatable("voicepunish.screen.settings.toggle_volume"), enableVolumeModeration), button -> {
            enableVolumeModeration = !enableVolumeModeration;
            button.setMessage(toggleLabel(Text.translatable("voicepunish.screen.settings.toggle_volume"), enableVolumeModeration));
        }).dimensions(leftX, startY, columnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(toggleLabel(Text.translatable("voicepunish.screen.settings.toggle_transcript"), enableTranscriptModeration), button -> {
            enableTranscriptModeration = !enableTranscriptModeration;
            button.setMessage(toggleLabel(Text.translatable("voicepunish.screen.settings.toggle_transcript"), enableTranscriptModeration));
        }).dimensions(rightX, startY, columnWidth, 20).build());

        minimumVolumeThresholdField = addNumberField(Text.translatable("voicepunish.screen.settings.volume_threshold").getString(), leftX, startY + rowHeight, columnWidth, formatDouble(currentSnapshot.minimumVolumeThreshold()));
        volumeMultiplierField = addNumberField(Text.translatable("voicepunish.screen.settings.volume_multiplier").getString(), rightX, startY + rowHeight, columnWidth, formatDouble(currentSnapshot.volumeMultiplier()));
        overThresholdHoldField = addNumberField(Text.translatable("voicepunish.screen.settings.hold_ms").getString(), leftX, startY + rowHeight * 2, columnWidth, String.valueOf(currentSnapshot.overThresholdHoldMs()));
        loudCooldownField = addNumberField(Text.translatable("voicepunish.screen.settings.cooldown_ms").getString(), rightX, startY + rowHeight * 2, columnWidth, String.valueOf(currentSnapshot.loudCooldownMs()));
        loudDamageField = addNumberField(Text.translatable("voicepunish.screen.settings.loud_damage").getString(), leftX, startY + rowHeight * 3, columnWidth, formatFloat(currentSnapshot.loudDamage()));
        loudEscalatedDamageField = addNumberField(Text.translatable("voicepunish.screen.settings.loud_damage_escalated").getString(), rightX, startY + rowHeight * 3, columnWidth, formatFloat(currentSnapshot.loudEscalatedDamage()));
        badWordDamageField = addNumberField(Text.translatable("voicepunish.screen.settings.badword_damage").getString(), leftX, startY + rowHeight * 4, columnWidth, formatFloat(currentSnapshot.badWordDamage()));
        badWordEscalatedDamageField = addNumberField(Text.translatable("voicepunish.screen.settings.badword_damage_escalated").getString(), rightX, startY + rowHeight * 4, columnWidth, formatFloat(currentSnapshot.badWordEscalatedDamage()));
        defaultEventCountField = addNumberField(Text.translatable("voicepunish.screen.settings.default_event_count").getString(), leftX, startY + rowHeight * 5, columnWidth, String.valueOf(currentSnapshot.defaultEventCount()));
        escalatedEventCountField = addNumberField(Text.translatable("voicepunish.screen.settings.escalated_event_count").getString(), rightX, startY + rowHeight * 5, columnWidth, String.valueOf(currentSnapshot.escalatedEventCount()));
        hostileMobWeightField = addNumberField(Text.translatable("voicepunish.screen.settings.mob_weight").getString(), leftX, startY + rowHeight * 6, columnWidth, String.valueOf(currentSnapshot.hostileMobWeight()));
        inventoryLossWeightField = addNumberField(Text.translatable("voicepunish.screen.settings.item_loss_weight").getString(), rightX, startY + rowHeight * 6, columnWidth, String.valueOf(currentSnapshot.inventoryLossWeight()));
        negativeEffectWeightField = addNumberField(Text.translatable("voicepunish.screen.settings.debuff_weight").getString(), leftX, startY + rowHeight * 7, columnWidth, String.valueOf(currentSnapshot.negativeEffectWeight()));
        teleportWeightField = addNumberField(Text.translatable("voicepunish.screen.settings.teleport_weight").getString(), rightX, startY + rowHeight * 7, columnWidth, String.valueOf(currentSnapshot.teleportWeight()));

        int actionY = startY + rowHeight * 8 + 8;
        addDrawableChild(ButtonWidget.builder(Text.translatable("voicepunish.screen.settings.bad_words"), button -> openBadWordsEditor())
                .dimensions(leftX, actionY, columnWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("voicepunish.screen.settings.save_apply"), button -> onSave())
                .dimensions(rightX, actionY, columnWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("voicepunish.screen.settings.cancel"), button -> close())
                .dimensions(this.width / 2 - 50, actionY + 26, 100, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(getTextRenderer(), getTitle(), this.width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(getTextRenderer(), Text.translatable("voicepunish.screen.settings.subtitle"), this.width / 2, 24, 0xFFA0A0A0);

        for (LabeledField labeledField : labeledFields) {
            context.drawTextWithShadow(getTextRenderer(), Text.literal(labeledField.label()), labeledField.x(), labeledField.y() - 10, 0xFFFFFFFF);
        }

        VoicePunishSettingsSnapshot preview = safePreviewSnapshot();
        if (preview != null) {
            String chances = String.format(
                    Locale.ROOT,
                    "Event chances: mob %.1f%% | item %.1f%% | debuff %.1f%% | teleport %.1f%%",
                    preview.normalizedChance(preview.hostileMobWeight()),
                    preview.normalizedChance(preview.inventoryLossWeight()),
                    preview.normalizedChance(preview.negativeEffectWeight()),
                    preview.normalizedChance(preview.teleportWeight())
            );
            context.drawCenteredTextWithShadow(getTextRenderer(), Text.literal(chances), this.width / 2, this.height - 52, 0xFFB0B0B0);
            context.drawCenteredTextWithShadow(getTextRenderer(), Text.translatable("voicepunish.screen.settings.badword_count", preview.badWords().size()), this.width / 2, this.height - 40, 0xFFB0B0B0);
        }

        context.drawCenteredTextWithShadow(getTextRenderer(), Text.translatable("voicepunish.screen.settings.badword_hint"), this.width / 2, this.height - 28, 0xFF909090);
        if (!statusMessage.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(getTextRenderer(), statusMessage, this.width / 2, this.height - 16, statusColor);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    public void updateBadWordsDraft(String badWordsText) {
        this.badWordsDraft = badWordsText;
        this.currentSnapshot = currentSnapshot.withBadWords(splitBadWords(badWordsText));
    }

    public String getBadWordsDraft() {
        return badWordsDraft;
    }

    public String getConfigPath() {
        return configPath;
    }

    private TextFieldWidget addNumberField(String label, int x, int y, int width, String initialValue) {
        TextFieldWidget field = new TextFieldWidget(getTextRenderer(), x, y, width, 20, Text.literal(label));
        field.setText(initialValue);
        field.setMaxLength(32);
        field.setDrawsBackground(true);
        addDrawableChild(field);
        labeledFields.add(new LabeledField(label, x, y));
        return field;
    }

    private void openBadWordsEditor() {
        VoicePunishSettingsSnapshot preview = safePreviewSnapshot();
        if (preview != null) {
            currentSnapshot = preview.withBadWords(splitBadWords(badWordsDraft));
        }
        MinecraftClient.getInstance().setScreen(new VoicePunishBadWordsScreen(this));
    }

    private void onSave() {
        VoicePunishSettingsSnapshot snapshot = safePreviewSnapshot();
        if (snapshot == null) {
            statusMessage = Text.translatable("voicepunish.screen.settings.error.number");
            statusColor = 0xFFFF5555;
            return;
        }

        String error = snapshot.validate();
        if (error != null) {
            statusMessage = Text.literal(error);
            statusColor = 0xFFFF5555;
            return;
        }

        currentSnapshot = snapshot;
        ClientVoiceRuntimeState.applySettingsSnapshot(snapshot);
        ClientPlayNetworking.send(new SaveConfigEditorPayload(snapshot));
        MinecraftClient.getInstance().setScreen(null);
    }

    private VoicePunishSettingsSnapshot safePreviewSnapshot() {
        try {
            return new VoicePunishSettingsSnapshot(
                    enableVolumeModeration,
                    enableTranscriptModeration,
                    Double.parseDouble(minimumVolumeThresholdField.getText()),
                    Double.parseDouble(volumeMultiplierField.getText()),
                    Long.parseLong(overThresholdHoldField.getText()),
                    Long.parseLong(loudCooldownField.getText()),
                    Float.parseFloat(loudDamageField.getText()),
                    Float.parseFloat(loudEscalatedDamageField.getText()),
                    Float.parseFloat(badWordDamageField.getText()),
                    Float.parseFloat(badWordEscalatedDamageField.getText()),
                    Integer.parseInt(defaultEventCountField.getText()),
                    Integer.parseInt(escalatedEventCountField.getText()),
                    Integer.parseInt(hostileMobWeightField.getText()),
                    Integer.parseInt(inventoryLossWeightField.getText()),
                    Integer.parseInt(negativeEffectWeightField.getText()),
                    Integer.parseInt(teleportWeightField.getText()),
                    splitBadWords(badWordsDraft)
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> splitBadWords(String input) {
        String normalized = input.replace(",", "\n").replace("，", "\n").replace(";", "\n").replace("；", "\n");
        String[] pieces = normalized.split("\\R+");
        List<String> words = new ArrayList<>();
        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        return VoicePunishSettingsSnapshot.sanitizeBadWords(words);
    }

    private Text toggleLabel(Text prefix, boolean enabled) {
        return Text.empty()
                .append(prefix)
                .append(Text.literal(": "))
                .append(Text.translatable(enabled ? "voicepunish.common.on" : "voicepunish.common.off"));
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record LabeledField(String label, int x, int y) {
    }
}
