package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack

@Environment(EnvType.CLIENT)
class MusicPlayerReceiver( private val musicPlayer: MusicPlayerEntity ) : GenericReceiver() {

    override fun close() { println( Base.DEBUG_NAME + "[$musicPlayer] device has been closed." ) }

    override fun init() {

        entity = musicPlayer.companion

        val instruments = mutableListOf<ItemStack>()
        for ( i in 0..15 ) instruments.add( musicPlayer.getStack(i) )
        this.instruments = instruments

    }

    override fun shouldCancel(): Boolean {

        val b = super.shouldCancel();       if (b) return true
        
        val sequencer = musicPlayer.sequencer ?: return false

        val isDone = sequencer.tickPosition == sequencer.tickLength
        if ( isDone && musicPlayer.isPlaying ) {
            musicPlayer.clientPause(true);      return true
        }

        return false

    }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        val instrument = stack.item
        val index = instruments.indexOf(stack)
        return instrument is Instrument && index == channel

    }

    override fun onPlay( sound: CustomSoundInstance, stack: ItemStack, entity: Entity ) {

        val client = MinecraftClient.getInstance()
        var skipClient = false;     val instrument = stack.item as Instrument

        if ( CanBeMuted.blacklist.keys.contains(entity) ) {
            instrument.stopAllNotes(stack, client.world);   skipClient = true
        }

        sound.playSound(stack, skipClient, true)

    }

}
