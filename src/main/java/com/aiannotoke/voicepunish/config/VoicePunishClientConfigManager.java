package com.aiannotoke.voicepunish.config;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoicePunishClientConfigManager {

    private final Path configPath;
    private final Gson gson;

    public VoicePunishClientConfigManager(Path configPath) {
        this.configPath = configPath;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public synchronized VoicePunishClientConfig loadOrCreate() {
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            if (!Files.exists(configPath)) {
                VoicePunishClientConfig config = new VoicePunishClientConfig();
                config.fillDefaults();
                save(config);
                return config;
            }
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                VoicePunishClientConfig config = gson.fromJson(reader, VoicePunishClientConfig.class);
                if (config == null) {
                    config = new VoicePunishClientConfig();
                }
                config.fillDefaults();
                return config;
            }
        } catch (Exception exception) {
            VoicePunishMod.LOGGER.error("Failed to load client config {}, using defaults", configPath, exception);
            VoicePunishClientConfig fallback = new VoicePunishClientConfig();
            fallback.fillDefaults();
            return fallback;
        }
    }

    public synchronized void save(VoicePunishClientConfig config) {
        config.fillDefaults();
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
        } catch (IOException exception) {
            VoicePunishMod.LOGGER.error("Failed to save client config {}", configPath, exception);
        }
    }

    public Path getConfigPath() {
        return configPath;
    }
}
