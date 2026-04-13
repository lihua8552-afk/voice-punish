package com.aiannotoke.voicepunish.state;

import com.aiannotoke.voicepunish.config.VoicePunishConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVoiceStateTest {

    @Test
    void clientLevelsUseConfiguredThresholdInsteadOfFixedOne() {
        VoicePunishConfig config = new VoicePunishConfig();
        config.minimumVolumeThreshold = 0.18D;
        config.overThresholdHoldMs = 200L;
        config.loudCooldownMs = 0L;
        config.rollingWindowMs = 500L;

        PlayerVoiceState state = new PlayerVoiceState();

        assertFalse(state.recordClientLevel(0.12F, 100L, config).shouldPunish());
        assertFalse(state.recordClientLevel(0.50F, 200L, config).shouldPunish());
        assertTrue(state.recordClientLevel(0.50F, 300L, config).shouldPunish());
    }
}
