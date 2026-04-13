package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.service.VoiceModerationService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VoicePunishNetworking {

    private static boolean commonRegistered;

    private VoicePunishNetworking() {
    }

    public static void registerCommon(VoiceModerationService service) {
        if (commonRegistered) {
            return;
        }
        commonRegistered = true;

        PayloadTypeRegistry.playS2C().register(OpenConfigEditorPayload.ID, OpenConfigEditorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VoiceHudPayload.ID, VoiceHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VoiceRecognitionHintsPayload.ID, VoiceRecognitionHintsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigEditorPayload.ID, SaveConfigEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoiceLevelPayload.ID, VoiceLevelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoiceTranscriptPayload.ID, VoiceTranscriptPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigEditorPayload.ID, (payload, context) ->
                service.applyEditorSnapshot(context.player(), payload.snapshot())
        );
        ServerPlayNetworking.registerGlobalReceiver(VoiceLevelPayload.ID, (payload, context) ->
                service.onClientVoiceLevel(context.player(), payload.level())
        );
        ServerPlayNetworking.registerGlobalReceiver(VoiceTranscriptPayload.ID, (payload, context) ->
                service.onClientTranscript(context.player(), payload.transcript())
        );
    }
}
