package com.enginemachiner.honkytones;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Arrays;
import java.util.List;

import static com.enginemachiner.honkytones.UtilityKt.getHands;

// This is used in for mixins
public class HonkyTonesMixinLogic {
    static Object[] mobs = {
            ZombieEntity.class, ZombieVillagerEntity.class,
            HuskEntity.class, DrownedEntity.class,
            SkeletonEntity.class, StrayEntity.class,
            PiglinEntity.class, PiglinBruteEntity.class,
            PillagerEntity.class, VindicatorEntity.class,
            ZombifiedPiglinEntity.class, WitherSkeletonEntity.class
    };

    static public boolean canPlay(Class<MobEntity> classy) {
        List<Object> list = Arrays.stream(mobs).filter((p) -> p.equals(classy)).toList();
        return list.size() > 0;
    }

    static public boolean forceAttack(PlayerEntity ply, Hand hand, LivingEntity entity) {

        ItemStack[] array = { ply.getMainHandStack(), ply.getOffHandStack() };

        for ( ItemStack stack : array ) {

            Item item = stack.getItem();

            boolean b = item instanceof Instrument && ply.isInSneakingPose();
            Hand[] hands = getHands();
            
            int index = 0;
            for ( int i = 0; i < array.length; i++ ) {
                if ( array[i] == stack ) { index = i; break; }
            }

            if (b) {
                item.useOnEntity(stack, ply, entity, hands[index]);
                return true;
            }

        }

        return false;

    }

}
