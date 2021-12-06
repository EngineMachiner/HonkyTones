package com.enginemachiner.honkytones

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import javax.sound.midi.*

var sequenceBind: KeyBinding? = null
var restartBind: KeyBinding? = null

class MIDIReceiver(id: String) : Receiver {

    private var currentInstrument = ""
    private var localSounds = mutableMapOf<Int, HonkyTonesSoundInstance?>()

    private val midiID = id

    override fun close() { println("$this is off!") }

    override fun send(message: MidiMessage?, timeStamp: Long) {

        val client = MinecraftClient.getInstance()
        val ply = client.player ?: return

        var item = ply.mainHandStack.item

        val noteInt = message!!.message[1].toInt()
        val volume = message.message[2].toFloat()

        if (noteInt in 25..95 && item.group == honkyTonesGroup) {

            item = item as Instrument

            if (currentInstrument.isEmpty() || currentInstrument != item.instrumentName ) {
                currentInstrument = item.instrumentName.toString()
                localSounds = mutableMapOf()
            }

            if (item.state != "Play") { return }

            if ( volume > 0 ) {

                // C3 to B7
                val start = 36

                var midiIndex = start
                var octaveIndex = 1
                var rangeCounter = 1;      var range = 2    // Starting range
                while ( midiIndex != noteInt ) {

                    midiIndex += 1
                    octaveIndex += 1
                    rangeCounter += 1

                    if ( rangeCounter > 12 ) {
                        rangeCounter = 1
                        range += 1
                    }

                    if ( octaveIndex > 12 ) { octaveIndex = 1 }

                }

                val selectedNote = octave.elementAt(octaveIndex - 1)
                val selectedRange = range

                val format = formatNote(selectedNote, selectedRange)
                val sound = item.getNote(item, format)

                sound.volume = volume / 90f

                if (sound.id.path.isNotEmpty()) {
                    localSounds[noteInt] = sound
                    playSound(sound, ply, " ID: $midiID-$noteInt")
                }

            } else {

                if ( localSounds[noteInt] != null ) {
                    stopSound(localSounds[noteInt]!!, "$midiID-$noteInt")
                }

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
                if ( device.maxTransmitters != 0 ) {

                    controllersMap[device] = mutableMapOf()
                    val formerMap = controllersMap[device]!!

                    val receiverList = mutableListOf<Receiver>()
                    val transmitterList = device.transmitters

                    for (trans in transmitterList) {
                        val receiver = MIDIReceiver("${device.deviceInfo}")
                        trans.receiver = receiver
                        receiverList.add(receiver)
                    }

                    val formerTransmitter = device.transmitter
                    formerTransmitter.receiver = MIDIReceiver("${device.deviceInfo}")
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