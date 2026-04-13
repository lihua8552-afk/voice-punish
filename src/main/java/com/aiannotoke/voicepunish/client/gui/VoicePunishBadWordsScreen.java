package com.aiannotoke.voicepunish.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

public class VoicePunishBadWordsScreen extends Screen {

    private static final String HIDDEN_PLACEHOLDER = "Bad words are hidden by default. Press Reveal to view and edit them.";

    private final VoicePunishConfigScreen parent;
    private EditBoxWidget badWordsEditor;
    private boolean revealed;
    private String draftText;

    public VoicePunishBadWordsScreen(VoicePunishConfigScreen parent) {
        super(Text.translatable("voicepunish.screen.badwords.title"));
        this.parent = parent;
        this.draftText = parent.getBadWordsDraft();
        this.revealed = false;
    }

    @Override
    protected void init() {
        clearChildren();

        int editorWidth = Math.min(340, this.width - 40);
        int editorX = this.width / 2 - editorWidth / 2;
        int editorY = 52;
        int editorHeight = Math.max(120, this.height - 128);

        badWordsEditor = EditBoxWidget.builder()
                .x(editorX)
                .y(editorY)
                .hasBackground(true)
                .hasOverlay(true)
                .placeholder(Text.translatable("voicepunish.screen.badwords.placeholder"))
                .build(getTextRenderer(), editorWidth, editorHeight, Text.translatable("voicepunish.screen.badwords.list"));
        badWordsEditor.setMaxLength(8192);
        badWordsEditor.setMaxLines(256);
        badWordsEditor.setChangeListener(text -> {
            if (revealed) {
                draftText = text;
            }
        });
        addDrawableChild(badWordsEditor);

        applyRevealState();

        int buttonY = editorY + editorHeight + 8;
        addDrawableChild(ButtonWidget.builder(toggleRevealLabel(), button -> {
            if (revealed) {
                draftText = badWordsEditor.getText();
                revealed = false;
            } else {
                revealed = true;
            }
            applyRevealState();
            button.setMessage(toggleRevealLabel());
        }).dimensions(editorX, buttonY, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("voicepunish.screen.badwords.save"), button -> {
            if (revealed) {
                draftText = badWordsEditor.getText();
            }
            parent.updateBadWordsDraft(draftText);
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width / 2 - 50, buttonY, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("voicepunish.screen.badwords.back"), button -> {
            if (revealed) {
                draftText = badWordsEditor.getText();
            }
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(editorX + editorWidth - 110, buttonY, 110, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(getTextRenderer(), getTitle(), this.width / 2, 14, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(getTextRenderer(), Text.translatable("voicepunish.screen.badwords.subtitle"), this.width / 2, 28, 0xFFA0A0A0);
        context.drawCenteredTextWithShadow(getTextRenderer(), Text.translatable("voicepunish.screen.badwords.footer"), this.width / 2, this.height - 16, 0xFF909090);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void applyRevealState() {
        if (revealed) {
            badWordsEditor.active = true;
            badWordsEditor.setText(draftText.isBlank() ? "" : draftText);
            badWordsEditor.setFocused(true);
        } else {
            badWordsEditor.active = false;
            badWordsEditor.setFocused(false);
            badWordsEditor.setText(HIDDEN_PLACEHOLDER, false);
        }
    }

    private Text toggleRevealLabel() {
        return Text.translatable(revealed ? "voicepunish.screen.badwords.hide" : "voicepunish.screen.badwords.reveal");
    }
}
