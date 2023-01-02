package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.HonkyTonesMixinLogic;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

// DrownedEntity.class and PillagerEntity.class are ranged big sad
@Mixin( {
        MobEntity.class, AbstractSkeletonEntity.class,
        PiglinEntity.class, PiglinBruteEntity.class,
        VindicatorEntity.class, ZombifiedPiglinEntity.class,
        WitherSkeletonEntity.class
} )
public class MobsCanPlay {

    /** Allow mobs to spawn with instruments */
    @SuppressWarnings("unchecked")
    @Inject(at = @At("TAIL"), method = "initEquipment")
    private void honkyTonesAddChanceToHaveInstruments(
            net.minecraft.util.math.random.Random random, LocalDifficulty diff, CallbackInfo callback
    ) {

        MobEntity mob = (MobEntity) (Object) this;
        Class<MobEntity> mobClass = (Class<MobEntity>) mob.getClass();
        boolean onRandom = new Random().nextInt(8) + 1 == 8;
        boolean canPlay = HonkyTonesMixinLogic.canPlay(mobClass);

        if ( onRandom && canPlay ) {

            Object[] names = Instrument.Companion.getClasses().values().toArray();
            int index = new Random().nextInt(names.length);
            String name = (String) names[index];

            Identifier id = new Identifier(Base.MOD_NAME + ":" + name);
            Item inst = Registry.ITEM.get(id);

            mob.equipStack( EquipmentSlot.MAINHAND, new ItemStack(inst) );

        }

    }

}

