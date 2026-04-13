package com.aiannotoke.voicepunish.service;

import com.aiannotoke.voicepunish.VoicePunishMod;
import com.aiannotoke.voicepunish.config.VoicePunishConfig;
import com.aiannotoke.voicepunish.punishment.PunishmentCause;
import com.aiannotoke.voicepunish.punishment.PunishmentEventType;
import com.aiannotoke.voicepunish.punishment.PunishmentRoll;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class PunishmentEngine {

    public PunishmentRoll apply(ServerPlayerEntity player, PunishmentCause cause, float damage, int eventCount, boolean escalated, VoicePunishConfig config) {
        List<String> eventSummaries = new ArrayList<>();

        if (damage > 0F) {
            applyDamage(player, damage);
        }

        for (int i = 0; i < eventCount; i++) {
            PunishmentEventType type = rollEventType(config);
            String summary = applyEvent(player, type, config);
            eventSummaries.add(summary);
            VoicePunishMod.LOGGER.info("[PunishEvent] {} -> {}", player.getGameProfile().name(), summary);
        }

        return new PunishmentRoll(cause, damage, escalated, List.copyOf(eventSummaries));
    }

    private void applyDamage(ServerPlayerEntity player, float damage) {
        player.damage(player.getEntityWorld(), player.getDamageSources().generic(), damage);
    }

    private PunishmentEventType rollEventType(VoicePunishConfig config) {
        int total = config.hostileMobWeight + config.inventoryLossWeight + config.negativeEffectWeight + config.teleportWeight;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(total, 1));
        if (roll < config.hostileMobWeight) {
            return PunishmentEventType.HOSTILE_MOB;
        }
        roll -= config.hostileMobWeight;
        if (roll < config.inventoryLossWeight) {
            return PunishmentEventType.DELETE_RANDOM_ITEM;
        }
        roll -= config.inventoryLossWeight;
        if (roll < config.negativeEffectWeight) {
            return PunishmentEventType.NEGATIVE_EFFECT;
        }
        return PunishmentEventType.SAFE_TELEPORT;
    }

    private String applyEvent(ServerPlayerEntity player, PunishmentEventType type, VoicePunishConfig config) {
        return switch (type) {
            case HOSTILE_MOB -> spawnHostileMob(player, config);
            case DELETE_RANDOM_ITEM -> deleteRandomInventoryItem(player, config);
            case NEGATIVE_EFFECT -> applyNegativeEffect(player, config);
            case SAFE_TELEPORT -> safeTeleport(player, config);
        };
    }

    private String spawnHostileMob(ServerPlayerEntity player, VoicePunishConfig config) {
        ServerWorld world = player.getEntityWorld();
        if (config.hostileMobs == null || config.hostileMobs.isEmpty()) {
            return applyNegativeEffect(player, config);
        }
        for (int i = 0; i < config.mobSpawnAttempts; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0D, Math.PI * 2D);
            double radius = ThreadLocalRandom.current().nextDouble(config.mobSpawnMinDistance, config.mobSpawnMaxDistance + 1D);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!isSafeStandingSpot(world, pos)) {
                continue;
            }
            String mobId = config.hostileMobs.get(ThreadLocalRandom.current().nextInt(config.hostileMobs.size()));
            EntityType<?> entityType = EntityType.get(mobId).orElse(null);
            if (entityType == null || !entityType.isSummonable()) {
                continue;
            }
            var spawned = entityType.spawn(world, pos, SpawnReason.COMMAND);
            if (spawned == null) {
                continue;
            }
            spawned.refreshPositionAndAngles(x + 0.5D, y, z + 0.5D, ThreadLocalRandom.current().nextFloat() * 360F, 0F);
            if (spawned instanceof MobEntity mobEntity) {
                mobEntity.setPersistent();
                mobEntity.setTarget(player);
            }
            return "随机刷出怪物: " + mobId;
        }
        return applyNegativeEffect(player, config);
    }

    private String deleteRandomInventoryItem(ServerPlayerEntity player, VoicePunishConfig config) {
        PlayerInventory inventory = player.getInventory();
        int size = inventory.size();
        for (int i = 0; i < config.itemLossRetries; i++) {
            int slot = ThreadLocalRandom.current().nextInt(size);
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack removed = stack.copy();
            inventory.setStack(slot, ItemStack.EMPTY);
            inventory.markDirty();
            return "随机删除物品: " + removed.getName().getString() + " x" + removed.getCount();
        }
        return applyNegativeEffect(player, config);
    }

    private String applyNegativeEffect(ServerPlayerEntity player, VoicePunishConfig config) {
        if (config.negativeEffects == null || config.negativeEffects.isEmpty()) {
            return "事件回退失败: 没有可用负面效果";
        }
        String effectId = config.negativeEffects.get(ThreadLocalRandom.current().nextInt(config.negativeEffects.size()));
        int seconds = ThreadLocalRandom.current().nextInt(config.effectMinSeconds, config.effectMaxSeconds + 1);
        int amplifier = effectId.equals("minecraft:poison") ? 0 : 1;
        Identifier effectIdentifier = Identifier.tryParse(effectId);
        if (effectIdentifier == null) {
            return "事件回退失败: 无效效果 " + effectId;
        }
        var effectEntry = Registries.STATUS_EFFECT.getEntry(effectIdentifier);
        if (effectEntry.isEmpty()) {
            return "事件回退失败: 未找到效果 " + effectId;
        }
        player.addStatusEffect(new StatusEffectInstance(effectEntry.get(), seconds * 20, amplifier, false, true, true));
        return "获得负面效果: " + effectId + " (" + seconds + "s)";
    }

    private String safeTeleport(ServerPlayerEntity player, VoicePunishConfig config) {
        ServerWorld world = player.getEntityWorld();
        for (int i = 0; i < config.teleportAttempts; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0D, Math.PI * 2D);
            double radius = ThreadLocalRandom.current().nextDouble(config.teleportMinRadius, config.teleportMaxRadius + 1D);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!isSafeStandingSpot(world, pos)) {
                continue;
            }
            boolean teleported = player.teleport(world, x + 0.5D, y, z + 0.5D, Set.<PositionFlag>of(), player.getYaw(), player.getPitch(), false);
            if (teleported) {
                return "安全随机传送至: " + x + ", " + y + ", " + z;
            }
        }
        return applyNegativeEffect(player, config);
    }

    private boolean isSafeStandingSpot(World world, BlockPos pos) {
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }
        BlockPos feet = pos;
        BlockPos head = pos.up();
        BlockPos ground = pos.down();
        if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(head).isEmpty()) {
            return false;
        }
        if (!world.isAir(feet) || !world.isAir(head)) {
            return false;
        }
        return world.getBlockState(ground).isSideSolidFullSquare(world, ground, Direction.UP);
    }

}
