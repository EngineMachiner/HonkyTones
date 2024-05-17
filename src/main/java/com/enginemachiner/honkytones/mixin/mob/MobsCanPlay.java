package com.enginemachiner.honkytones.mixin.mob;

import com.enginemachiner.harmony.ItemKt;
import com.enginemachiner.honkytones.MixinLogic;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import kotlin.reflect.KClass;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// TODO: Consider making ranged mobs to shoot projectiles.

@Mixin( {

        MobEntity.class, AbstractSkeletonEntity.class,
        PiglinEntity.class, PiglinBruteEntity.class,
        VindicatorEntity.class, ZombifiedPiglinEntity.class,
        WitherSkeletonEntity.class,

        PillagerEntity.class, DrownedEntity.class

} )
public abstract class MobsCanPlay extends LivingEntity {

    protected MobsCanPlay( EntityType<? extends LivingEntity> entityType, World world ) {
        super( entityType, world );
    }

    /** Allow mobs to spawn with instruments. */
    @Inject( at = @At("TAIL"), method = "initEquipment" )
    private void honkyTonesAddChanceToHaveInstruments(
            Random random, LocalDifficulty difficulty, CallbackInfo callback
    ) {

        boolean onRandom = random.nextInt(10) + 1 == 10;
        boolean canPlay = MixinLogic.canPlay( getClass() );

        if ( !onRandom || !canPlay ) return;


        KClass<?>[] classes = getClasses();
        int i = random.nextInt( classes.length );
        KClass<?> kClass = classes[i];
        Item instrument = ItemKt.modItem(kClass);

        equipStack( EquipmentSlot.MAINHAND, new ItemStack(instrument) );

    }

    @Unique
    private KClass<?>[] getClasses() {
        return Instrument.Companion.getClasses().toArray( new KClass[0] );
    }

}

