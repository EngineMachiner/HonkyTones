package com.enginemachiner.honkytones.mixins;

import com.enginemachiner.honkytones.BaseKt;
import com.enginemachiner.honkytones.Instrument;
import com.enginemachiner.honkytones.MobLogicMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public class MobTick {
    MobLogicMixin logic = new MobLogicMixin();

    @Inject(at = @At("HEAD"), method = "tick")
    private void tick(CallbackInfo info) {

        World world = MinecraftClient.getInstance().world;
        MobEntity mob = (MobEntity) (Object) this;
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();

        boolean b = !mob.isAttacking();
        b = b && logic.canPlay((Class<MobEntity>) mob.getClass());
        b = b && mob.isAlive() && !mob.isAiDisabled();
        b = b && logic.isInGame();

        if (b && item instanceof Instrument inst) {
            NbtCompound nbt = stack.getNbt();
            int timer = nbt.getInt("mobTick");
            int delay = (int) BaseKt.getCommands().get("mobsPlayingDelay");
            if (Random.create().nextInt(3) == 0) {
                if (timer < delay) {
                    nbt.putInt("mobTick", timer + 1);
                } else {
                    nbt.putInt("mobTick", 0);
                    inst.playRandomSound(mob);
                    inst.stopAllNotes(world);
                }
            }
        }

    }
}
