package com.aiannotoke.voicepunish.mixin;

import com.aiannotoke.voicepunish.client.audio.VoskUtf8Bridge;
import com.pryzmm.speech.SpeechRecognizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vosk.Recognizer;

@Mixin(SpeechRecognizer.class)
public abstract class SpeechRecognizerMixin {

    @Shadow
    public Recognizer recognizer;

    @Inject(method = "getStringMsg", at = @At("HEAD"), cancellable = true)
    private void voicepunish$useUtf8Result(byte[] data, CallbackInfoReturnable<String> cir) {
        if (recognizer == null || data == null) {
            cir.setReturnValue("");
            return;
        }

        if (recognizer.acceptWaveForm(data, data.length)) {
            cir.setReturnValue(VoskUtf8Bridge.getResultText(recognizer));
        } else {
            cir.setReturnValue("");
        }
    }
}
