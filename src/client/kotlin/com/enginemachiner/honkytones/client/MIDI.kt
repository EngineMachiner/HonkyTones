package com.enginemachiner.honkytones.client

import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.client.items.instruments.InstrumentReceiver
import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.HarmonyItem.Companion.trackHolder
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.world
import com.enginemachiner.honkytones.client.sound.InstrumentSound
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import javax.sound.midi.*
import javax.sound.midi.Receiver


object MIDI {

    /** Sets and links midi transmitters and receivers. */
    fun setup() {

        MidiSystem.getMidiDeviceInfo().forEach {

            val device = MidiSystem.getMidiDevice(it);      val info = device.deviceInfo

            if ( device.maxTransmitters == 0 ) return@forEach


            device.transmitter.receiver = InstrumentReceiver( info.name )

            if ( device.isOpen ) return@forEach


            try {

                device.open();      modPrint( "MIDI device found: $info." )

            } catch( e: MidiUnavailableException ) {

                modPrint( "MIDI device $info is unavailable." );        e.printStackTrace()

            }

        }

    }

    fun hasSystemSequencer(): Boolean {

        try { MidiSystem.getSequencer() } catch ( exception: Exception ) {

            Message( "error.midi_sequencer", exception ).console()

            return false

        }

        return true

    }

}


abstract class AbstractReceiver : Receiver {

    var entity: Entity? = null;         var instruments = mutableListOf<ItemStack>()

    override fun send( message: MidiMessage, timeStamp: Long ) {

        world() ?: return

        if ( message !is ShortMessage ) return;         client().send { onSend(message) }

    }

    abstract fun setData();         open fun volume(): Float { return 1f }

    abstract fun canPlay( stack: ItemStack, channel: Int ): Boolean

    open fun canSend(): Boolean { return true }

    open fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        trackHolder(stack, entity);       sound.play(stack)

        sound.isManual = false

    }

    private fun onSend( message: ShortMessage ) {

        setData();          val entity = entity ?: return;          if ( client().isPaused ) return


        val channel = message.channel;          val command = message.command


        instruments.forEach {

            val canPlay = canPlay( it, channel );       if ( !canPlay ) return@forEach


            val copy = soundsCopy(it);      val sounds = copy.device()

            val index = copy.pos( message.data1 )


            if ( index > sounds.size ) return@forEach

            val sound = sounds[index] ?: return@forEach


            // MIDI volume and instrument volume.

            val volume = message.data2 * volume() / 127f


            val isNoteOn = command == ShortMessage.NOTE_ON
            val isNoteOff = command == ShortMessage.NOTE_OFF

            val play = volume > 0 && isNoteOn

            val stop = ( isNoteOn && volume == 0f ) || isNoteOff


            wrap(sound) {

                sound.canSend = canSend()

                when {

                    stop -> sound.fadeOut()

                    play -> {

                        val volume2 = nbt(it).getFloat("Volume")

                        sound.maxVolume = volume * volume2

                        onPlay( sound, it, entity )

                    }

                }

            }


        }

    }

    /** Wraps the networking state of the sound. Saves the state if it changes in action. */
    protected fun wrap( sound: InstrumentSound, function: () -> Unit ) {

        function();           sound.canSend = true

    }

}
