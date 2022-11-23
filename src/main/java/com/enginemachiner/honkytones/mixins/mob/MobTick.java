package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.HonkyTonesMixinLogic;
import com.enginemachiner.honkytones.Network;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

import static com.enginemachiner.honkytones.BaseKt.getServerConfig;

@Mixin(MobEntity.class)
public class MobTick {

    /** For mobs that already have an instrument, they play notes on a random tick interval */
    @SuppressWarnings("unchecked")
    @Inject(at = @At("HEAD"), method = "tick")
    private void honkyTonesMobPlayOnInterval(CallbackInfo callback) {

        MobEntity mob = (MobEntity) (Object) this;
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();

        boolean b = !mob.isAttacking();
        b = b && HonkyTonesMixinLogic.canPlay( (Class<MobEntity>) mob.getClass() );
        b = b && mob.isAlive() && !mob.isAiDisabled();
        b = b && Network.INSTANCE.canNetwork() && !mob.world.isClient;

        if (b && item instanceof Instrument instrument) {

            MinecraftClient client = MinecraftClient.getInstance();

            if ( !stack.getOrCreateTag().contains( Base.MOD_NAME ) ) {
                instrument.loadNbtData(stack);
            }

            NbtCompound nbt = stack.getTag().getCompound( Base.MOD_NAME );
            int timer = nbt.getInt("mobTick");
            int delay = (int) getServerConfig().get("mobsPlayingDelay");

            if ( new Random().nextInt(3) == 0 ) {

                if ( timer < delay ) { nbt.putInt("mobTick", timer + 1); }
                else {

                    nbt.putInt("mobTick", 0);
                    stack.setHolder(mob);
                    instrument.spawnNoteParticle(mob.world, mob);

                    client.send( () -> {
                        instrument.playRandomSound(stack);
                        instrument.stopAllNotes(stack, client.world);
                    } );

                }

            }

        }

    }
}
