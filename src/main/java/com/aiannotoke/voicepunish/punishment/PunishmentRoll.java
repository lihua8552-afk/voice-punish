package com.aiannotoke.voicepunish.punishment;

import java.util.List;

public record PunishmentRoll(
        PunishmentCause cause,
        float damage,
        boolean escalated,
        List<String> eventSummaries
) {
}
