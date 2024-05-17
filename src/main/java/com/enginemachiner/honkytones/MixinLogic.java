package com.enginemachiner.honkytones;

import com.enginemachiner.harmony.ItemKt;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Arrays;

public class MixinLogic {

    private static final Class<?>[] mobs = {
            ZombieEntity.class,                 ZombieVillagerEntity.class,
            HuskEntity.class,                   DrownedEntity.class,
            SkeletonEntity.class,               StrayEntity.class,
            PiglinEntity.class,                 PiglinBruteEntity.class,
            PillagerEntity.class,               VindicatorEntity.class,
            ZombifiedPiglinEntity.class,        WitherSkeletonEntity.class
    };

    static public boolean canPlay( Class<?> mobClass ) {
        return Arrays.asList(mobs).contains(mobClass);
    }

    static public boolean tryForceAttack( PlayerEntity player, MobEntity entity ) {

        int i = 0;      ItemStack[] stacks = { player.getMainHandStack(), player.getOffHandStack() };

        for ( ItemStack stack : stacks ) {

            Item item = stack.getItem();        Hand[] hands = ItemKt.getHands();

            boolean canAttack = item instanceof Instrument && player.isInSneakingPose();

            if ( !canAttack ) { i++; continue; }

            item.useOnEntity( stack, player, entity, hands[i] );

            return true;

        }

        return false;

    }

}
