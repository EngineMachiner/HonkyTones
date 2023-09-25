package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.ItemKt;
import com.enginemachiner.honkytones.MixinLogic;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import kotlin.reflect.KClass;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// DrownedEntity.class and PillagerEntity.class are ranged mobs, they can't use instruments, big sad.

@Mixin( {
        MobEntity.class, AbstractSkeletonEntity.class,
        PiglinEntity.class, PiglinBruteEntity.class,
        VindicatorEntity.class, ZombifiedPiglinEntity.class,
        WitherSkeletonEntity.class
} )
public class MobsCanPlay {

    /** Allow mobs to spawn with instruments. */
    @Inject( at = @At("TAIL"), method = "initEquipment" )
    private void honkyTonesAddChanceToHaveInstruments(
            Random random, LocalDifficulty difficulty, CallbackInfo callback
    ) {

        MobEntity mob = (MobEntity) (Object) this;
        Object mobClass = mob.getClass();

        boolean onRandom = Random.create().nextInt(8) + 1 == 8;
        boolean canPlay = MixinLogic.canPlay(mobClass);

        if ( !onRandom || !canPlay ) return;

        Object[] names = Instrument.Companion.getClasses().toArray();

        int index = Random.create().nextInt( names.length );
        KClass<Instrument> kclass = ( KClass<Instrument> ) names[index];

        Item instrument = ItemKt.modItem(kclass);

        mob.equipStack( EquipmentSlot.MAINHAND, new ItemStack(instrument) );

    }

}

