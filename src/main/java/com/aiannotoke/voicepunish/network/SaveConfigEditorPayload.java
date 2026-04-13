package com.aiannotoke.voicepunish.network;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishSettingsSnapshot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveConfigEditorPayload(
        VoicePunishSettingsSnapshot snapshot
) implements CustomPayload {

    public static final CustomPayload.Id<SaveConfigEditorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoicePunishMod.MOD_ID, "save_config_editor"));
    public static final PacketCodec<RegistryByteBuf, SaveConfigEditorPayload> CODEC = PacketCodec.of(SaveConfigEditorPayload::write, SaveConfigEditorPayload::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(RegistryByteBuf buf) {
        snapshot.write(buf);
    }

    public static SaveConfigEditorPayload read(RegistryByteBuf buf) {
        return new SaveConfigEditorPayload(VoicePunishSettingsSnapshot.read(buf));
    }
}
