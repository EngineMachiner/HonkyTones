package com.enginemachiner.honkytones.mixins.sound;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
@Mixin( SoundSystem.class )
public class SoundSystemMixin {

    private static final String target = "Lnet/minecraft/client/sound/SoundLoader;loadStreamed(Lnet/minecraft/util/Identifier;Z)Ljava/util/concurrent/CompletableFuture;";

    /** Implementation to use streams */
    @Redirect(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At( value = "INVOKE", target = target )
    )
    private CompletableFuture<?> honkyTonesGetStream( SoundLoader loader, Identifier id,
                                                      boolean looping, SoundInstance sound ) {
        return sound.getAudioStream(loader, id, looping);
    }

}