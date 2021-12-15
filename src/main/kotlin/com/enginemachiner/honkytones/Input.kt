package com.enginemachiner.honkytones

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import org.lwjgl.glfw.GLFW
import javax.sound.midi.*

var sequenceBind: KeyBinding? = null
var restartBind: KeyBinding? = null

private fun mapCheck(map: MutableMap<Int, HTSoundInstance?>): MutableMap<Int, HTSoundInstance?> {
    val newList = mutableMapOf<Int, HTSoundInstance?>()
    for ( instanceI in map ) {
        if (!instanceI.value!!.isDone) { newList[instanceI.key] = instanceI.value }
    }
    return newList.toMutableMap()
}

class MIDIReceiver(id: String) : Receiver {

    private fun findStack(channel: Byte, player: PlayerEntity): ItemStack? {

        for ( v in player.inventory.main ) {
            if (v.item.group == honkyTonesGroup) {
                val tag = v.orCreateNbt
                val uniqueCase = tag.getString("MIDI Device Name") == midiID
                val caseOne = uniqueCase && ( tag.getInt("MIDI Channel") - 113 ).toUInt() == channel.toUInt()
                val caseTwo = uniqueCase && ( tag.getInt("MIDI Channel") - 113 ).toUInt() == ( channel + 16 ).toUInt()
                if ( caseOne || caseTwo ) { return v }
            }
        }

        return null

    }

    private var currentInstrument = ""
    private var localSounds = mutableMapOf< Int, MutableMap<Int, HTSoundInstance?> >()

    private val midiID = id

    override fun close() {}

    override fun send(message: MidiMessage?, timeStamp: Long) {

        val client = MinecraftClient.getInstance()
        val ply = client.player ?: return

        val channel = message!!.message[0]
        val stack = findStack(channel, ply)

        val noteInt = message.message[1].toInt()
        var volume = 1f
        if ( message.message.size > 2 ) {
            volume = message.message[2].toFloat()
        }

        if (stack == null) { return }

        val tag = stack.orCreateNbt
        val channelTag = tag.getInt("MIDI Channel")
        val inst = stack.item as Instrument

        if ( tag.getString("Action") != "Play" ) { return }

        if ( volume > 0 ) {

            val start = 0
            var midiIndex = start
            var octaveIndex = 1
            var rangeCounter = 1;      var range = -1    // Starting range

            while ( midiIndex != noteInt ) {

                midiIndex += 1
                octaveIndex += 1
                rangeCounter += 1

                if ( rangeCounter > 12 ) {
                    rangeCounter = 1
                    range += 1
                }

                if ( octaveIndex > 12 ) { octaveIndex = 1 }

                if ( midiIndex > 120 ) { return }

            }

            val selectedNote = octave.elementAt(octaveIndex - 1)
            val selectedRange = range

            val format = formatNote(selectedNote, selectedRange)
            val sound = inst.getNote(inst, format)

            if (sound.id.path.isNotEmpty()) {

                volume = volume / 100f + tag.getFloat("Volume") - 1f
                if ( volume <= 0 ) { return }
                sound.volume = volume

                if (localSounds[channelTag] == null) { localSounds[channelTag] = mutableMapOf() }

                if (currentInstrument != inst.instrumentName) {
                    currentInstrument = inst.instrumentName.toString()
                    localSounds[channelTag]!!.clear()
                }

                localSounds[channelTag] = mapCheck( localSounds[channelTag]!! )
                localSounds[channelTag]!![noteInt] = sound
                val data = " ID: $midiID-$noteInt"

                client.send { playSound(sound, ply, data) }

            }

        } else {

            if ( localSounds[channelTag] == null || localSounds[channelTag]!![noteInt] == null ) { return }
            val sound = localSounds[channelTag]!![noteInt]!!
            // This isn't working
            if ( inst.instrumentName != "drumset" ) {
                client.send { stopSound(sound, "$midiID-$noteInt") }
            }

        }

    }

}

val controllersMap = mutableMapOf<MidiDevice, MutableMap< List<Transmitter>, List<Receiver> > >()

class Input : ClientModInitializer {

    override fun onInitializeClient() {

        // MIDI setup
        val midiInfo = MidiSystem.getMidiDeviceInfo()
        if ( midiInfo.isNotEmpty() ) {

            for ( deviceInfo in midiInfo ) {

                val device = MidiSystem.getMidiDevice(deviceInfo)

                // MIDI Devices with max transmitters == -1 are weird
                if ( device.maxTransmitters != 0 ) {

                    controllersMap[device] = mutableMapOf()
                    val formerMap = controllersMap[device]!!

                    val receiverList = mutableListOf<Receiver>()
                    val transmitterList = device.transmitters

                    for (trans in transmitterList) {
                        val receiver = MIDIReceiver(device.deviceInfo.name)
                        trans.receiver = receiver
                        receiverList.add(receiver)
                    }

                    val formerTransmitter = device.transmitter
                    formerTransmitter.receiver = MIDIReceiver(device.deviceInfo.name)
                    formerMap[transmitterList] = receiverList

                    if ( !device.isOpen ) {
                        try {
                            device.open()
                            println(" [HONKYTONES]: MIDI device found: ${device.deviceInfo}")
                        } catch(e: MidiUnavailableException) {
                            println(" [HONKYTONES]: MIDI device ${device.deviceInfo} is unavailable.")
                        }
                    }

                }

            }

        }

        val id = Base.MOD_ID

        sequenceBind = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.$id.sequence",      InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,        "category.$id" )
        )

        restartBind = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.$id.restart",      InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,        "category.$id" )
        )

    }

}