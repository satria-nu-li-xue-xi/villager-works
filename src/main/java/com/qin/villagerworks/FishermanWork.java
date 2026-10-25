package com.qin.villagerworks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FishermanWork {

    // Each fisherman's chosen fishing spot (by his unique ID).
    private static final Map<UUID, BlockPos> SPOTS = new HashMap<>();
    // The game tick when each fisherman's next catch happens.
    private static final Map<UUID, Long> NEXT_CATCH = new HashMap<>();

    public static void tick(ServerLevel level, Villager villager) {
        long dayTime = level.getOverworldClockTime() % 24000;
        if (dayTime < 2000 || dayTime >= 9000) {
            SPOTS.remove(villager.getUUID());
            NEXT_CATCH.remove(villager.getUUID());
            return;
        }

        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isEmpty() || jobSite.get().dimension() != level.dimension()) {
            return;
        }

        BlockPos spot = SPOTS.get(villager.getUUID());
        if (spot == null) {
            spot = findShore(level, jobSite.get().pos());
            if (spot == null) {
                return;
            }
            SPOTS.put(villager.getUUID(), spot);
        }

        if (villager.blockPosition().distSqr(spot) > 4) {
            villager.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.6);
            return;
        }

        // Arrived: splash particles, and catch something when the timer is up.
        level.sendParticles(ParticleTypes.FISHING,
                spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5,
                6, 0.6, 0.1, 0.6, 0.0);

        long now = level.getGameTime();
        // First time here: schedule the first catch 5 to 10 seconds from now.
        long next = NEXT_CATCH.computeIfAbsent(villager.getUUID(),
                id -> now + 100 + villager.getRandom().nextInt(100));
        if (now >= next) {
            catchSomething(level, villager);
            // Next catch in 10 to 20 seconds.
            NEXT_CATCH.put(villager.getUUID(), now + 200 + villager.getRandom().nextInt(200));
        }
    }

    private static void catchSomething(ServerLevel level, Villager villager) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .create(LootContextParamSets.FISHING);

        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(BuiltInLootTables.FISHING);

        for (ItemStack stack : table.getRandomItems(params)) {
            // For now the catch goes into the villager's own inventory.
            // If there's no room, it is skipped (the barrel comes in milestone 4).
            if (villager.getInventory().canAddItem(stack)) {
                villager.getInventory().addItem(stack);
                System.out.println("[VillagerWorks] caught: " + stack);
            }
        }

        level.playSound(null, villager.blockPosition(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
    }

    private static BlockPos findShore(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    boolean standable = level.getBlockState(pos).isAir()
                            && level.getBlockState(pos.below()).isSolid();
                    if (!standable) {
                        continue;
                    }
                    boolean touchesWater = false;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        if (level.getFluidState(pos.below().relative(dir)).is(FluidTags.WATER)) {
                            touchesWater = true;
                        }
                    }
                    if (touchesWater && (best == null
                            || pos.distSqr(center) < best.distSqr(center))) {
                        best = pos;
                    }
                }
            }
        }
        return best;
    }
}