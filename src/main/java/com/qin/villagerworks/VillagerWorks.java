package com.qin.villagerworks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;

public class VillagerWorks implements ModInitializer {

    @Override
    public void onInitialize() {
        // Run method at the end of every level tick.
        ServerTickEvents.END_LEVEL_TICK.register(this::onLevelTick);
    }

    private void onLevelTick(ServerLevel level) {
        // Only check once per second (20 ticks) to keep the game fast.
        if (level.getGameTime() % 20 != 0) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            // Find villagers within 64 blocks of this player.
            var villagers = level.getEntities(
                    EntityTypeTest.forClass(Villager.class),
                    player.getBoundingBox().inflate(64),
                    villager -> true);

            for (Villager villager : villagers) {
                boolean isFisherman = villager.getVillagerData()
                        .profession().is(VillagerProfession.FISHERMAN);
                if (!isFisherman) {
                    continue;
                }
                if (!villager.getMainHandItem().is(Items.FISHING_ROD)) {
                    villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                }
                FishermanWork.tick(level, villager);
            }
        }
    }
}