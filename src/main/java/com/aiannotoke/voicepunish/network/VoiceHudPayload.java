package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VoiceHudPayload(
        String transcript,
        int[] highlightStarts,
        int[] highlightEnds,
        float volumePercentOfThreshold,
        boolean thresholdExceeded
) implements CustomPayload {

    public static final CustomPayload.Id<VoiceHudPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "voice_hud"));
    public static final PacketCodec<RegistryByteBuf, VoiceHudPayload> CODEC = PacketCodec.of(VoiceHudPayload::write, VoiceHudPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        buf.writeString(transcript);
        buf.writeIntArray(highlightStarts);
        buf.writeIntArray(highlightEnds);
        buf.writeFloat(volumePercentOfThreshold);
        buf.writeBoolean(thresholdExceeded);
    }

    public static VoiceHudPayload read(RegistryByteBuf buf) {
        return new VoiceHudPayload(
                buf.readString(),
                buf.readIntArray(),
                buf.readIntArray(),
                buf.readFloat(),
                buf.readBoolean()
        );
    }

    public boolean hasTranscript() {
        return transcript != null && !transcript.isBlank();
    }
}
