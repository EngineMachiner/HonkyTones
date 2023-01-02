package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.InstrumentReceiver
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import javax.sound.midi.*

@Environment(EnvType.CLIENT)
class Input : ClientModInitializer {

    private val devicesMap = mutableMapOf< MidiDevice, MutableMap< List<Transmitter>, List<Receiver> > >()

    private fun checkDevices() {

        // MIDI setup
        val midiInfo = MidiSystem.getMidiDeviceInfo()

        if ( midiInfo.isNotEmpty() ) {

            for ( deviceInfo in midiInfo ) {

                val device = MidiSystem.getMidiDevice(deviceInfo)

                // MIDI Devices with max transmitters == -1 are weird
                if ( device.maxTransmitters != 0 ) {

                    devicesMap[device] = mutableMapOf()
                    val formerMap = devicesMap[device]!!

                    val receiverList = mutableListOf<Receiver>()
                    val transmitterList = device.transmitters

                    for (trans in transmitterList) {
                        val receiver = InstrumentReceiver(device.deviceInfo.name)
                        trans.receiver = receiver
                        receiverList.add(receiver)
                    }

                    val formerTransmitter = device.transmitter
                    formerTransmitter.receiver = InstrumentReceiver(device.deviceInfo.name)
                    formerMap[transmitterList] = receiverList

                    if ( !device.isOpen ) {
                        try {
                            device.open()
                            println( Base.DEBUG_NAME + "MIDI device found: ${device.deviceInfo}" )
                        } catch(e: MidiUnavailableException) {
                            println( Base.DEBUG_NAME + "MIDI device ${device.deviceInfo} is unavailable.")
                            e.printStackTrace()
                        }
                    }

                }

            }

        }

    }

    override fun onInitializeClient() {

        checkDevices()

        Instrument.registerKeyBindings()
        DigitalConsoleScreen.registerKeyBindings()

    }

}

@Environment(EnvType.CLIENT)
abstract class GenericReceiver : Receiver {

    var entity: Entity? = null
    var instruments = mutableListOf<ItemStack>()

    override fun send( msg: MidiMessage?, timeStamp: Long ) {

        val client = MinecraftClient.getInstance()

        if ( msg !is ShortMessage ) return

        client.send {

            init()
            if ( shouldCancel() || entity == null ) return@send

            val entity = entity!!

            val channel = msg.channel
            val command = msg.command

            for ( stack in instruments ) {

                val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
                if ( canPlay( stack, channel ) ) {

                    val instrument = stack.item as Instrument
                    val sounds = instrument.getSounds(stack, "notes")
                    val index = instrument.getIndexIfCentered(stack, msg.data1)
                    val sound = sounds[index] ?: return@send
                    val volume = msg.data2 / 127f

                    val isNoteOn = command == ShortMessage.NOTE_ON
                    val isNoteOff = command == ShortMessage.NOTE_OFF
                    if ( isNoteOn && stack.holder != entity ) stack.holder = entity

                    val shouldPlay = volume > 0 && isNoteOn

                    val shouldStop = ( isNoteOff || ( isNoteOn && volume == 0f ) )
                            && instrument !is DrumSet

                    if ( shouldPlay ) {

                        sound.volume = volume * nbt.getFloat("Volume")

                        onPlay( sound, stack, entity )

                    } else if ( shouldStop ) onStop( sound, stack, entity )

                }

            }

        }

    }

    abstract fun init()
    abstract fun canPlay( stack: ItemStack, channel: Int ): Boolean

    open fun shouldCancel(): Boolean {

        val client = MinecraftClient.getInstance()
        val screen = client.currentScreen
        return screen != null && screen.shouldPause()

    }
    
    open fun onPlay( sound: CustomSoundInstance, stack: ItemStack, entity: Entity ) {
        sound.playSound(stack)
    }

    open fun onStop( sound: CustomSoundInstance, stack: ItemStack, entity: Entity ) {
        sound.stopSound(stack)
    }

}
