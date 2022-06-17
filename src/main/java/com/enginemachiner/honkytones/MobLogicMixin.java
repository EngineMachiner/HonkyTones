package com.enginemachiner.honkytones;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.*;

import java.util.Arrays;
import java.util.List;

// This is used in for mixins
public class MobLogicMixin {
    Object[] mobs = {
            ZombieEntity.class, ZombieVillagerEntity.class,
            HuskEntity.class, DrownedEntity.class,
            SkeletonEntity.class, StrayEntity.class,
            PiglinEntity.class, PiglinBruteEntity.class,
            PillagerEntity.class, VindicatorEntity.class,
            ZombifiedPiglinEntity.class, WitherSkeletonEntity.class
    };

    public boolean canPlay(Class<MobEntity> classy) {
        List<Object> list = Arrays.stream(mobs).filter((p) -> p.equals(classy)).toList();
        return list.size() > 0;
    }

    // Not sure if this is the right way to check if the game loaded
    public boolean isInGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean b = client.isInSingleplayer() && !client.isPaused();
        b = b || !client.isInSingleplayer();
        return b;
    }

}
