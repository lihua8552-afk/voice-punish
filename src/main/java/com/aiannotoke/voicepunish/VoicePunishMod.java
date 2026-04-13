package com.aiannotoke.voicepunish;

import com.aiannotoke.voicepunish.command.VoicePunishCommands;
import com.aiannotoke.voicepunish.config.ConfigManager;
import com.aiannotoke.voicepunish.network.VoicePunishNetworking;
import com.aiannotoke.voicepunish.service.VoiceModerationService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoicePunishMod implements ModInitializer {

    public static final String MOD_ID = "voicepunish";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ConfigManager configManager;
    private static VoiceModerationService service;

    @Override
    public void onInitialize() {
        configManager = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("voice-punish.json5"));
        service = new VoiceModerationService(configManager);
        VoicePunishNetworking.registerCommon(service);

        CommandRegistrationCallback.EVENT.register(VoicePunishCommands::register);
        ServerLifecycleEvents.SERVER_STARTED.register(service::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(service::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(service::onEndServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> service.onPlayerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> service.onPlayerLeft(handler.player));

        LOGGER.info("Voice Punish initialized");
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static VoiceModerationService getService() {
        return service;
    }
}
