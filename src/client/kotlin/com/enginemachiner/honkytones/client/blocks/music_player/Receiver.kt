package com.enginemachiner.honkytones.client.blocks.music_player

import com.enginemachiner.harmony.HarmonyItem.Companion.trackHolder
import com.enginemachiner.harmony.client.player
import com.enginemachiner.harmony.client.world
import com.enginemachiner.harmony.modPrint
import com.enginemachiner.honkytones.client.AbstractReceiver
import com.enginemachiner.honkytones.client.Silencer.isMuted
import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.client.sound.InstrumentSound
import com.enginemachiner.honkytones.items.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack

class MusicPlayerReceiver( private val musicPlayer: MusicPlayer ) : AbstractReceiver() {

    override fun close() { modPrint("$entity: Device has been closed.") }

    override fun setData() {

        entity = musicPlayer.entity();          instruments = musicPlayer.items

    }

    private fun stop() { musicPlayer.stopSequencer();   musicPlayer.spawnParticles = false }

    override fun volume(): Float {

        val floppy = musicPlayer.floppy();          if ( floppy.isEmpty ) return 0f

        val settings = FloppyDisk.settings( floppy, player() )

        val volume = settings.getDouble("Volume")

        return volume.toFloat()

    }

    override fun canSend(): Boolean { return false }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        if ( world() == null ) { stop();    return false }

        val instrument = stack.item;    val index = instruments.indexOf(stack)

        return instrument is InstrumentItem && index == channel

    }

    override fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        sound.isManual = true

        trackHolder( stack, entity );         val copy = soundsCopy(stack)

        if ( isMuted(entity) ) { copy.device.stop(); return }

        wrap(sound) { copy.play( sound, stack ) }

    }

}
