package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.harmony.Trackable
import com.enginemachiner.harmony.modPrint
import com.enginemachiner.harmony.player
import com.enginemachiner.harmony.world
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.GenericReceiver
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.sound.InstrumentSound
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack

// @Environment(EnvType.CLIENT)
class MusicPlayerReceiver( private val musicPlayer: MusicPlayer ) : GenericReceiver() {

    override fun close() { modPrint("$entity: Device has been closed.") }

    override fun setData() {

        entity = musicPlayer.blockEntity!!.entity!!

        val instruments = mutableListOf<ItemStack>()

        for ( i in 0..15 ) instruments.add( musicPlayer.item(i) )

        this.instruments = instruments

    }

    private fun stop() { musicPlayer.stopSequencer();   musicPlayer.spawnParticles = false }

    override fun volume(): Float {

        val floppy = musicPlayer.item(0)

        val settings = FloppyDisk.settings( floppy, player() )

        val volume = settings.getDouble("Volume")

        return volume.toFloat()

    }

    override fun shouldNetwork(): Boolean { return false }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        if ( world() == null ) { stop();    return false }

        val instrument = stack.item;    val index = instruments.indexOf(stack)

        return instrument is Instrument && index == channel

    }

    override fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        val instrument = stack.item as Instrument

        Trackable.trackHolder( stack, entity )

        if ( isMuted(entity) ) { instrument.stopDeviceSounds(stack); return }

        wrap(sound) { sound.play(stack) }

    }

}
