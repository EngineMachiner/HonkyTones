package com.enginemachiner.honkytones.mixin.mob;

import com.enginemachiner.honkytones.MixinLogic;
import com.enginemachiner.honkytones.NBT;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

import static com.enginemachiner.honkytones.ConfigKt.serverConfig;

@Mixin( MobEntity.class )
public class MobEntityMixin {

    @Unique
    private static final Instrument.Companion companion = Instrument.Companion;

    /** Make mobs play instruments when attacking. */
    @Inject( at = @At("HEAD"), method = "tryAttack" )
    private void honkyTonesPlaySoundAttacking( Entity target, CallbackInfoReturnable<Boolean> callback ) {

        MobEntity mob = (MobEntity) (Object) this;
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();

        boolean isInstrument = item instanceof Instrument;
        if ( !isInstrument ) return;

        int random1 = new Random().nextInt(2);
        int random2 = new Random().nextInt(2);

        // Play minimum one instrument sound.

        for ( int i = 1; ( i < 2 + random1 + random2 ); i++ ) {
            stack.setHolder(mob);   companion.mobPlay(mob);
        }

    }

    /** For mobs that already have an instrument, they play notes on a random tick interval. */
    @Inject( at = @At("HEAD"), method = "tick" )
    private void honkyTonesMobPlayOnInterval( CallbackInfo callback ) {

        MobEntity mob = (MobEntity) (Object) this;
        Object mobClass = mob.getClass();
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();
        World world = mob.getWorld();

        boolean isInstrument = item instanceof Instrument;
        boolean b = !mob.isAttacking() && MixinLogic.canPlay(mobClass);
        b = b && mob.isAlive() && !mob.isAiDisabled() && !world.isClient;
        if ( !b || !isInstrument ) return;

        Instrument instrument = (Instrument) item;
        if ( !NBT.has(stack) ) instrument.setupNBT(stack);

        NbtCompound nbt = NBT.get(stack);

        int timer = nbt.getInt("MobTick");
        int delay = (int) serverConfig.get("mobs_playing_delay");

        if ( new Random().nextInt(3) != 0 ) return;

        if ( timer < delay ) nbt.putInt( "MobTick", timer + 1 ); else {

            nbt.putInt( "MobTick", 0 );       stack.setHolder(mob);

            companion.mobPlay(mob);

        }

    }

}
