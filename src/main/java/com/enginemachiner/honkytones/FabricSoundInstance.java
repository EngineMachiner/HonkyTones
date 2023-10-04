package com.enginemachiner.honkytones;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

/** Implementation to play audio streams taken from Fabric.
 * <a href="https://github.com/FabricMC/fabric/pull/2558">...</a>
 */
public interface FabricSoundInstance {

    default CompletableFuture<AudioStream> getAudioStream(
            SoundLoader loader, Identifier id, boolean repeatInstantly
    ) { return loader.loadStreamed(id, repeatInstantly); }

}
