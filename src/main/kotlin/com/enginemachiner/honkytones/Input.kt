package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.InstrumentReceiver
import com.enginemachiner.honkytones.sound.InstrumentSound
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.util.Language
import javax.sound.midi.*

@Environment(EnvType.CLIENT)
object Midi {

    /** Sets and links midi transmitters and receivers. */
    fun configDevices() {

        MidiSystem.getMidiDeviceInfo().forEach {

            val device = MidiSystem.getMidiDevice(it);      val info = device.deviceInfo

            if ( device.maxTransmitters == 0 ) return@forEach

            device.transmitter.receiver = InstrumentReceiver( info.name )

            if ( device.isOpen ) return@forEach

            // To consider opening the device when the world loads and closing on exit.

            try {

                device.open();      modPrint( "MIDI device found: $info." )

            } catch( e: MidiUnavailableException ) {

                modPrint( "MIDI device $info is unavailable." )

                e.printStackTrace()

            }

        }

    }

    fun hasSystemSequencer(): Boolean {

        try { MidiSystem.getSequencer() } catch ( e: Exception ) {

            val key = "error.midi_sequencer"

            if ( Language.getInstance().hasTranslation(key) ) {

                warnUser( Translation.get(key) )
                warnUser( Translation.get("message.check_console") )

            } else modPrint( "ERROR: Couldn't load MIDI Devices!" )

            e.printStackTrace();        return false

        }

        return true

    }

}

@Environment(EnvType.CLIENT)
abstract class GenericReceiver : Receiver {

    var entity: Entity? = null;     var instruments = mutableListOf<ItemStack>()

    override fun send( message: MidiMessage, timeStamp: Long ) {

        if ( message !is ShortMessage ) return;     client().send { onSend(message) }

    }

    abstract fun setData()

    abstract fun canPlay( stack: ItemStack, channel: Int ): Boolean

    open fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        checkHolder( stack, entity );    sound.play(stack)

    }

    fun checkHolder( stack: ItemStack, entity: Entity ) { if ( stack.holder != entity ) stack.holder = entity }

    private fun onSend(message: ShortMessage) {

        setData();      if ( client().isPaused ) return

        val entity = entity!!;      val channel = message.channel;      val command = message.command

        instruments.forEach {

            val canPlay = canPlay( it, channel )

            if ( !canPlay ) return@forEach;         val nbt = NBT.get(it)

            val instrument = it.item as Instrument
            val sounds = instrument.stackSounds(it).deviceNotes
            val index = instrument.soundIndex( it, message.data1 )

            if ( index > sounds.size ) return@forEach

            val sound = sounds[index] ?: return@forEach

            val volume = message.data2 / 127f

            val isNoteOn = command == ShortMessage.NOTE_ON
            val isNoteOff = command == ShortMessage.NOTE_OFF

            val play = volume > 0 && isNoteOn

            var stop = ( isNoteOn && volume == 0f ) || isNoteOff

            // Make sure the stack is the same and not null.
            // It can happen when playing midi and switching channels at the same time.
            stop = stop && sound.stack == it

            if (play) {

                sound.maxVolume = volume * nbt.getFloat("Volume")

                onPlay( sound, it, entity )

            } else if (stop) sound.fadeOut()

        }

    }

}
