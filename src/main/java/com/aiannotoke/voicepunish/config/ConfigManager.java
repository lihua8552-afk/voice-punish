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

public class ConfigManager {

    private final Path configPath;
    private final Gson gson;

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public synchronized VoicePunishConfig loadOrCreate() {
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            if (!Files.exists(configPath)) {
                VoicePunishConfig config = new VoicePunishConfig();
                save(config);
                return config;
            }
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                VoicePunishConfig config = gson.fromJson(reader, VoicePunishConfig.class);
                if (config == null) {
                    config = new VoicePunishConfig();
                }
                double rawThreshold = config.minimumVolumeThreshold;
                config.fillDefaults();
                if (Double.compare(rawThreshold, config.minimumVolumeThreshold) != 0) {
                    VoicePunishMod.LOGGER.warn(
                            "Normalized minimumVolumeThreshold from {} to {} while loading {}",
                            rawThreshold,
                            config.minimumVolumeThreshold,
                            configPath
                    );
                }
                return config;
            }
        } catch (Exception e) {
            VoicePunishMod.LOGGER.error("Failed to load config {}, using defaults", configPath, e);
            VoicePunishConfig fallback = new VoicePunishConfig();
            fallback.fillDefaults();
            return fallback;
        }
    }

    public synchronized VoicePunishConfig reload() {
        VoicePunishConfig config = loadOrCreate();
        save(config);
        return config;
    }

    public synchronized void save(VoicePunishConfig config) {
        config.fillDefaults();
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            VoicePunishMod.LOGGER.error("Failed to save config {}", configPath, e);
        }
    }

    public Path getConfigPath() {
        return configPath;
    }
}
