package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.Network;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import kotlin.random.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobInstrumentAttack {

    /** Make mobs play instruments if they have one on hand when attacking */
    @Inject(at = @At("HEAD"), method = "tryAttack")
    private void honkyTonesPlayInstrumentOnAttack( Entity target,
                                                   CallbackInfoReturnable<Boolean> callback ) {

        World world = MinecraftClient.getInstance().world;
        MobEntity mob = (MobEntity) (Object) this;
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();
        boolean b = Network.INSTANCE.canNetwork();

        if ( item instanceof Instrument inst && b ) {

            MinecraftClient client = MinecraftClient.getInstance();

            int keyChance1 = Random.Default.nextInt(2);
            int keyChance2 = Random.Default.nextInt(2);

            // Play minimum 1 note
            for ( int i = 1; ( i < 2 + keyChance1 + keyChance2 ); i++ ) {
                stack.setHolder(mob);
                inst.spawnNoteParticle(world, mob);
                client.send( () -> inst.playRandomSound(stack) );
            }
            client.send( () -> inst.stopAllNotes(stack, world) );

        }

    }

}
