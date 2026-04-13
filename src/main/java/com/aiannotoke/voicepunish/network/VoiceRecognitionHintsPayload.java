package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record VoiceRecognitionHintsPayload(
        List<String> hints
) implements CustomPayload {

    public static final CustomPayload.Id<VoiceRecognitionHintsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "voice_recognition_hints"));
    public static final PacketCodec<RegistryByteBuf, VoiceRecognitionHintsPayload> CODEC =
            PacketCodec.of(VoiceRecognitionHintsPayload::write, VoiceRecognitionHintsPayload::read);

    public VoiceRecognitionHintsPayload {
        hints = List.copyOf(hints);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        buf.writeInt(hints.size());
        for (String hint : hints) {
            buf.writeString(hint);
        }
    }

    public static VoiceRecognitionHintsPayload read(RegistryByteBuf buf) {
        int size = buf.readInt();
        List<String> hints = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            hints.add(buf.readString());
        }
        return new VoiceRecognitionHintsPayload(hints);
    }
}
