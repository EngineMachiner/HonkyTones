package com.enginemachiner.honkytones.mixin;

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin( ClientWorld.class )
public class ClientWorldMixin {

    @Inject( at = @At("HEAD"), method = "disconnect" )
    private void pauseAllMidiMusicPlayers( CallbackInfo callback ) {
        MusicPlayer.Companion.getList().forEach( MusicPlayer::pauseOnMidiHost );
    }

}
