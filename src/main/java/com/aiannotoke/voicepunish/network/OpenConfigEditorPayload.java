package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishSettingsSnapshot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenConfigEditorPayload(
        VoicePunishSettingsSnapshot snapshot,
        String configPath
) implements CustomPayload {

    public static final CustomPayload.Id<OpenConfigEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "open_config_editor"));
    public static final PacketCodec<RegistryByteBuf, OpenConfigEditorPayload> CODEC = PacketCodec.of(OpenConfigEditorPayload::write, OpenConfigEditorPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        snapshot.write(buf);
        buf.writeString(configPath);
    }

    public static OpenConfigEditorPayload read(RegistryByteBuf buf) {
        return new OpenConfigEditorPayload(VoicePunishSettingsSnapshot.read(buf), buf.readString());
    }
}
