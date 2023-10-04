package com.enginemachiner.honkytones.mixin.sound;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;

/** Implementation to play audio streams taken from Fabric.
 * <a href="https://github.com/FabricMC/fabric/pull/2558">...</a>
 */
@Mixin( SoundSystem.class )
public class SoundSystemMixin {

    @Unique
    private static final String method = "play(Lnet/minecraft/client/sound/SoundInstance;)V";

    @Unique
    private static final String target = "Lnet/minecraft/client/sound/SoundLoader;loadStreamed(Lnet/minecraft/util/Identifier;Z)Ljava/util/concurrent/CompletableFuture;";

    @Redirect( at = @At( value = "INVOKE", target = target ), method = method )
    private CompletableFuture<?> honkyTonesGetStream(
            SoundLoader loader, Identifier id, boolean looping, SoundInstance sound
    ) { return sound.getAudioStream(loader, id, looping); }

}