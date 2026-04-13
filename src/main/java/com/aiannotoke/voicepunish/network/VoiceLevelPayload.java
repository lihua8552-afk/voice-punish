package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VoiceLevelPayload(
        float level
) implements CustomPayload {

    public static final CustomPayload.Id<VoiceLevelPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "voice_level"));
    public static final PacketCodec<RegistryByteBuf, VoiceLevelPayload> CODEC = PacketCodec.of(VoiceLevelPayload::write, VoiceLevelPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        buf.writeFloat(level);
    }

    public static VoiceLevelPayload read(RegistryByteBuf buf) {
        return new VoiceLevelPayload(buf.readFloat());
    }
}
