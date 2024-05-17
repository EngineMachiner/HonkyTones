package com.enginemachiner.honkytones.mixin.mob;

import com.enginemachiner.harmony.NBT;
import com.enginemachiner.honkytones.Config;
import com.enginemachiner.honkytones.MixinLogic;
import com.enginemachiner.honkytones.ServerConfigFile;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin( MobEntity.class )
public abstract class MobEntityMixin extends LivingEntity {

    protected MobEntityMixin( EntityType<? extends LivingEntity> entityType, World world ) {
        super( entityType, world );
    }

    @Shadow public abstract boolean isAttacking();

    @Shadow public abstract boolean isAiDisabled();

    @Unique
    private static final Instrument.Companion companion = Instrument.Companion;

    /** Make mobs play instruments when attacking. */
    @Inject( at = @At("HEAD"), method = "tryAttack" )
    private void honkyTonesMobPlayAttacking( Entity target, CallbackInfoReturnable<Boolean> callback ) {

        ItemStack stack = getMainHandStack();       Item item = stack.getItem();

        boolean isInstrument = item instanceof Instrument;


        if ( !isInstrument ) return;        stack.setHolder(this);

        // Play one instrument sound minimum.

        int random1 = new Random().nextInt(2);
        int random2 = new Random().nextInt(2);

        for ( int i = 1; ( i < 2 + random1 + random2 ); i++ ) {
            companion.mobPlay(this);
        }

    }

    /** For mobs that already have an instrument, they play notes on a random tick interval. */
    @Inject( at = @At("HEAD"), method = "tick" )
    private void honkyTonesMobPlayOnTick( CallbackInfo callback ) {

        ItemStack stack = getMainHandStack();       Item item = stack.getItem();

        boolean isInstrument = item instanceof Instrument;

        boolean allow = !isAttacking() && isAlive() && !isAiDisabled()
                && MixinLogic.canPlay( getClass() ) && !world.isClient;


        if ( !allow || !isInstrument ) return;      stack.setHolder(this);


        Instrument instrument = (Instrument) item;
        if ( !NBT.has(stack) ) instrument.setupNBT(stack);

        NbtCompound nbt = NBT.get(stack);
        int timer = nbt.getInt("mobTick");

        ServerConfigFile.Companion.Data config = Config.INSTANCE.server();
        int delay = config.getMobsPlayingDelay();

        if ( new Random().nextInt(3) != 0 ) return;


        if ( timer < delay ) nbt.putInt( "mobTick", timer + 1 ); else {

            nbt.putInt( "mobTick", 0 );     companion.mobPlay(this);

        }

    }

}
