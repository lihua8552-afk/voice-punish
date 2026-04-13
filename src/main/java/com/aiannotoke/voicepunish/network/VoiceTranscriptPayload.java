package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VoiceTranscriptPayload(
        String transcript
) implements CustomPayload {

    public static final CustomPayload.Id<VoiceTranscriptPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "voice_transcript"));
    public static final PacketCodec<RegistryByteBuf, VoiceTranscriptPayload> CODEC = PacketCodec.of(VoiceTranscriptPayload::write, VoiceTranscriptPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        buf.writeString(transcript);
    }

    public static VoiceTranscriptPayload read(RegistryByteBuf buf) {
        return new VoiceTranscriptPayload(buf.readString());
    }
}
