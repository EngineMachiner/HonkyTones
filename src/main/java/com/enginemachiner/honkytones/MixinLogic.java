package com.enginemachiner.honkytones;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.List;

/** These are used in the HonkyTones mixins. */
public class MixinLogic {
    static final Object[] mobs = {
            ZombieEntity.class,                 ZombieVillagerEntity.class,
            HuskEntity.class,                   DrownedEntity.class,
            SkeletonEntity.class,               StrayEntity.class,
            PiglinEntity.class,                 PiglinBruteEntity.class,
            PillagerEntity.class,               VindicatorEntity.class,
            ZombifiedPiglinEntity.class,        WitherSkeletonEntity.class
    };

    static public boolean canPlay( Object mobClass ) {

        List<Object> list = Arrays.stream(mobs)
                .filter( (p) -> p.equals(mobClass) )
                .toList();

        return !list.isEmpty();

    }

    static public boolean canForceAttack( PlayerEntity player, MobEntity entity ) {

        ItemStack[] array = { player.getMainHandStack(), player.getOffHandStack() };

        for ( ItemStack stack : array ) {

            Item item = stack.getItem();

            boolean canAttack = item instanceof Instrument && player.isInSneakingPose();
            Hand[] hands = ItemKt.getHands();
            
            int index = 0;
            for ( int i = 0; i < array.length; i++ ) {

                if ( array[i] == stack ) { index = i; break; }

            }

            if (canAttack) {

                item.useOnEntity( stack, player, entity, hands[index] );

                return true;

            }

        }

        return false;

    }

}
