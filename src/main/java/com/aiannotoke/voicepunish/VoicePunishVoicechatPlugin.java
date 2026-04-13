package com.aiannotoke.voicepunish;

import com.aiannotoke.voicepunish.service.VoiceModerationService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class VoicePunishVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return VoicePunishMod.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (VoicePunishMod.getService() != null) {
            VoicePunishMod.getService().setVoicechatApi(api);
        }
        VoicePunishMod.LOGGER.info("Voice Punish voicechat plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(PlayerConnectedEvent.class, this::onPlayerConnected);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
        registration.registerEvent(PlayerStateChangedEvent.class, this::onPlayerStateChanged);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onClientSound);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, this::onClientSound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::onClientSound);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        ServerPlayerEntity player = resolvePlayer(event.getSenderConnection());
        if (player == null) {
            return;
        }
        VoicePunishMod.getService().onMicrophonePacket(
                player,
                event.getPacket().getOpusEncodedData(),
                event.getPacket().isWhispering(),
                System.currentTimeMillis()
        );
    }

    private void onPlayerConnected(PlayerConnectedEvent event) {
        ServerPlayerEntity player = resolvePlayer(event.getConnection());
        if (player == null || player.getCommandSource().getServer() == null) {
            return;
        }
        player.getCommandSource().getServer().execute(() -> VoicePunishMod.getService().onVoicechatConnected(player));
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        UUID uuid = event.getPlayerUuid();
        VoiceModerationService service = VoicePunishMod.getService();
        if (service.getServer() != null) {
            service.getServer().execute(() -> service.onVoicechatDisconnected(uuid, false));
        }
    }

    private void onPlayerStateChanged(PlayerStateChangedEvent event) {
        VoiceModerationService service = VoicePunishMod.getService();
        if (service.getServer() == null) {
            return;
        }
        service.getServer().execute(() -> service.onVoicechatStateChanged(
                event.getPlayerUuid(),
                event.isDisconnected(),
                event.isDisabled()
        ));
    }

    private void onClientSound(ClientReceiveSoundEvent event) {
        if (VoicePunishMod.getService().shouldMuteIncomingVoicePlayback()) {
            event.cancel();
        }
    }

    private ServerPlayerEntity resolvePlayer(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) {
            return null;
        }
        Object player = connection.getPlayer().getPlayer();
        return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }
}
