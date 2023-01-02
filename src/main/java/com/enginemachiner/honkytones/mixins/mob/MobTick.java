package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.BaseKt;
import com.enginemachiner.honkytones.HonkyTonesMixinLogic;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(MobEntity.class)
public class MobTick {

    /** For mobs that already have an instrument, they play notes on a random tick interval */
    @SuppressWarnings("unchecked")
    @Inject(at = @At("HEAD"), method = "tick")
    private void honkyTonesMobPlayOnInterval(CallbackInfo callback) {

        MobEntity mob = (MobEntity) (Object) this;
        Class<MobEntity> mobClass = (Class<MobEntity>) mob.getClass();
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();
        World world = mob.world;

        boolean b = !mob.isAttacking();
        b = b && HonkyTonesMixinLogic.canPlay(mobClass);
        b = b && mob.isAlive() && !mob.isAiDisabled();
        b = b && !world.isClient;

        if ( b && item instanceof Instrument instrument ) {

            if ( !stack.getOrCreateTag().contains( Base.MOD_NAME ) ) {
                instrument.loadNbtData(stack);
            }

            NbtCompound nbt = stack.getTag().getCompound( Base.MOD_NAME );
            int timer = nbt.getInt("mobTick");
            int delay = (int) BaseKt.serverConfig.get("mobsPlayingDelay");

            if ( new Random().nextInt(3) == 0 ) {

                if ( timer < delay ) nbt.putInt("mobTick", timer + 1);
                else {

                    nbt.putInt("mobTick", 0);
                    stack.setHolder(mob);

                    Instrument.Companion.mobAction(mob);

                }

            }

        }

    }

}
