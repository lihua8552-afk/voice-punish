package com.aiannotoke.voicepunish;

import com.aiannotoke.voicepunish.client.audio.ClientAsrRuntimeState;
import com.aiannotoke.voicepunish.client.audio.ClientTranscriptionEngine;
import com.aiannotoke.voicepunish.client.audio.ClientVoiceRuntimeState;
import com.aiannotoke.voicepunish.client.gui.VoicePunishConfigScreen;
import com.aiannotoke.voicepunish.client.hud.VoiceHudState;
import com.aiannotoke.voicepunish.config.VoicePunishClientConfig;
import com.aiannotoke.voicepunish.config.VoicePunishClientConfigManager;
import com.aiannotoke.voicepunish.network.OpenConfigEditorPayload;
import com.aiannotoke.voicepunish.network.VoiceHudPayload;
import com.aiannotoke.voicepunish.network.VoiceLevelPayload;
import com.aiannotoke.voicepunish.network.VoiceRecognitionHintsPayload;
import com.aiannotoke.voicepunish.network.VoiceTranscriptPayload;
import com.aiannotoke.voicepunish.util.TextRepairUtil;
import com.pryzmm.ShriekClient;
import com.pryzmm.ShriekConstants;
import com.pryzmm.api.ShriekApi;
import com.pryzmm.client.event.EventHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class VoicePunishClient implements ClientModInitializer {

    private static final VoiceHudState HUD_STATE = new VoiceHudState();
    private static final String SHRIEK_MODEL_NAME = "vosk-model-small-cn-0.22";

    private static long lastVoiceLevelSentAt;
    private static ClientTranscriptionEngine transcriptionEngine;
    private static VoicePunishClientConfig clientConfig;
    private static long lastAsrStatusVersion = Long.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        VoicePunishClientConfigManager clientConfigManager = new VoicePunishClientConfigManager(
                FabricLoader.getInstance().getConfigDir().resolve("voice-punish-client.json5")
        );
        clientConfig = clientConfigManager.loadOrCreate();

        ClientPlayNetworking.registerGlobalReceiver(OpenConfigEditorPayload.ID, (payload, context) -> {
            ClientVoiceRuntimeState.applySettingsSnapshot(payload.snapshot());
            context.client().setScreen(new VoicePunishConfigScreen(payload.snapshot(), payload.configPath()));
        });
        ClientPlayNetworking.registerGlobalReceiver(VoiceHudPayload.ID, (payload, context) -> HUD_STATE.accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(VoiceRecognitionHintsPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (transcriptionEngine != null) {
                        transcriptionEngine.updateServerHints(payload.hints());
                    }
                })
        );

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HEALTH_BAR,
                Identifier.of(VoicePunishMod.MOD_ID, "voice_hud"),
                HUD_STATE::render
        );

        try {
            Path safeModelPath = Path.of(System.getProperty("user.home"), "voicepunish_vosk");
            ensureModelAvailableInSafePath(safeModelPath, SHRIEK_MODEL_NAME);
            EventHandler.setModelPath(safeModelPath);
            ShriekConstants.encoding_repair = false;
            EventHandler.loadVoskModel(SHRIEK_MODEL_NAME);

            Path shriekModelRoot = FabricLoader.getInstance().getGameDir().resolve("shriek").resolve("vosk");
            transcriptionEngine = new ClientTranscriptionEngine(clientConfig, safeModelPath, shriekModelRoot);
        } catch (Exception exception) {
            VoicePunishMod.LOGGER.error("Failed to initialize Shriek Chinese model", exception);
        }

        ShriekApi.registerClientSpeechListener(event -> {
            String transcript = TextRepairUtil.repairIfNeeded(event.getText());
            if (transcript != null) {
                transcript = transcript.trim();
            }
            if (transcript == null || transcript.isBlank()) {
                return;
            }

            if (transcriptionEngine != null) {
                transcriptionEngine.acceptFallbackTranscript(transcript);
                return;
            }
            if (ClientPlayNetworking.canSend(VoiceTranscriptPayload.ID)) {
                ClientPlayNetworking.send(new VoiceTranscriptPayload(transcript));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            announceAsrStatusIfNeeded(client);
            boolean canSendTranscript = client.player != null
                    && client.getNetworkHandler() != null
                    && ClientPlayNetworking.canSend(VoiceTranscriptPayload.ID);
            if (transcriptionEngine != null) {
                transcriptionEngine.onClientTick(ShriekClient.recordingSpeech);
                if (canSendTranscript) {
                    String transcript;
                    while ((transcript = transcriptionEngine.pollReadyTranscript()) != null) {
                        ClientPlayNetworking.send(new VoiceTranscriptPayload(transcript));
                    }
                }
            }

            if (client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastVoiceLevelSentAt < 50L) {
                return;
            }
            lastVoiceLevelSentAt = now;
            ClientPlayNetworking.send(new VoiceLevelPayload(ClientVoiceRuntimeState.getLatestModerationLevel()));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (transcriptionEngine != null) {
                transcriptionEngine.close();
            }
        });

        VoicePunishMod.LOGGER.info("Voice Punish client initialized");
    }

    private static void announceAsrStatusIfNeeded(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }
        ClientAsrRuntimeState.Snapshot snapshot = ClientAsrRuntimeState.snapshot();
        if (snapshot.version() == lastAsrStatusVersion || snapshot.status() == ClientAsrRuntimeState.Status.DISABLED) {
            return;
        }
        lastAsrStatusVersion = snapshot.version();
        switch (snapshot.status()) {
            case READY -> client.player.sendMessage(
                    net.minecraft.text.Text.literal("[Voice Punish] 本地中文语音转写已就绪")
                            .formatted(net.minecraft.util.Formatting.GREEN),
                    false
            );
            case ERROR -> client.player.sendMessage(
                    net.minecraft.text.Text.literal("[Voice Punish] 本地语音转写启动失败，当前回退到 Vosk")
                            .formatted(net.minecraft.util.Formatting.YELLOW),
                    false
            );
            default -> {
            }
        }
    }

    public static void captureTranscriptAudio(byte[] pcm) {
        if (transcriptionEngine != null) {
            transcriptionEngine.onAudioFrame(pcm);
        }
    }

    private static void ensureModelAvailableInSafePath(Path safeRoot, String modelName) throws IOException {
        Path safeModel = safeRoot.resolve(modelName);
        if (Files.exists(safeModel.resolve("am").resolve("final.mdl"))) {
            VoicePunishMod.LOGGER.info("Using existing safe Vosk model at {}", safeModel);
            return;
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path sourceModel = gameDir.resolve("shriek").resolve("vosk").resolve(modelName);
        if (!Files.exists(sourceModel.resolve("am").resolve("final.mdl"))) {
            VoicePunishMod.LOGGER.info("No existing local model found at {}, Shriek will download it if needed", sourceModel);
            return;
        }

        Files.createDirectories(safeRoot);
        VoicePunishMod.LOGGER.info("Copying Vosk model from {} to {}", sourceModel, safeModel);
        copyDirectory(sourceModel, safeModel);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }
}
