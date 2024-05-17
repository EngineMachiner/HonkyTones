package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.InstrumentReceiver
import com.enginemachiner.honkytones.sound.InstrumentSound
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.util.Language
import javax.sound.midi.*
import javax.sound.midi.Receiver

// @Environment(EnvType.CLIENT)
object MIDI {

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

            } catch( e: MidiUnavailableException) {

                modPrint( "MIDI device $info is unavailable." )

                e.printStackTrace()

            }

        }

    }

    fun hasSystemSequencer(): Boolean {

        try { MidiSystem.getSequencer() } catch ( e: Exception ) {

            val key = "error.midi_sequencer"

            if ( Language.getInstance().hasTranslation(key) ) warnConsole(key)
            else modPrint( "ERROR: Couldn't load MIDI Devices!" )

            e.printStackTrace();        return false

        }

        return true

    }

}

// @Environment(EnvType.CLIENT)
abstract class GenericReceiver : Receiver {

    var entity: Entity? = null;     var instruments = mutableListOf<ItemStack>()

    override fun send( message: MidiMessage, timeStamp: Long ) {

        if ( message !is ShortMessage ) return;     client().send { onSend(message) }

    }

    abstract fun setData();     open fun volume(): Float { return 1f }

    abstract fun canPlay( stack: ItemStack, channel: Int ): Boolean

    open fun shouldNetwork(): Boolean { return true }

    open fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        sound.isManual = true

        Trackable.trackHolder( stack, entity );    sound.play(stack)

        sound.isManual = false

    }

    private fun onSend(message: ShortMessage) {

        setData();      val entity = entity ?: return

        if ( client().isPaused ) return

        val channel = message.channel;      val command = message.command

        instruments.forEach {

            val canPlay = canPlay( it, channel )

            if ( !canPlay ) return@forEach

            val instrument = it.item as Instrument
            val sounds = instrument.stackSounds(it).deviceNotes
            val index = instrument.soundIndex( it, message.data1 )

            if ( index > sounds.size ) return@forEach

            val sound = sounds[index] ?: return@forEach

            val volume = message.data2 * volume() / 127f

            val isNoteOn = command == ShortMessage.NOTE_ON
            val isNoteOff = command == ShortMessage.NOTE_OFF

            val play = volume > 0 && isNoteOn

            var stop = ( isNoteOn && volume == 0f ) || isNoteOff

            /*
                Make sure the stack is the same and not null.
                It can happen when playing midi and switching channels at the same time.
             */

            stop = stop && sound.stack == it


            wrap(sound) {

                sound.shouldNetwork = shouldNetwork()


                if (stop) sound.fadeOut() else if (play) {

                    val instrumentVolume = NBT.get(it).getFloat("Volume")

                    sound.maxVolume = volume * instrumentVolume

                    onPlay( sound, it, entity )

                }

            }


        }

    }

    /** Wraps the networking state of the sound. Saves the state if it changes on action. */
    protected fun wrap( sound: InstrumentSound, action: () -> Unit ) {

        val shouldNetwork = sound.shouldNetwork;        action()

        sound.shouldNetwork = shouldNetwork

    }

}
