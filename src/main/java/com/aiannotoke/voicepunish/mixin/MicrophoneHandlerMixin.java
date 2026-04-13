package com.aiannotoke.voicepunish.mixin;

import com.aiannotoke.voicepunish.VoicePunishClient;
import com.aiannotoke.voicepunish.client.audio.ClientVoiceRuntimeState;
import com.pryzmm.speech.MicrophoneHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MicrophoneHandler.class)
public class MicrophoneHandlerMixin {

    @Inject(method = "readData", at = @At("RETURN"))
    private void voicepunish$captureMicrophoneLevel(CallbackInfoReturnable<byte[]> cir) {
        byte[] pcm = cir.getReturnValue();
        ClientVoiceRuntimeState.updateFromPcm(pcm);
        VoicePunishClient.captureTranscriptAudio(pcm);
    }
}
