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

var sequenceMenuBind: KeyBinding? = null
var sequenceResetBind: KeyBinding? = null

class MIDIReceiver(private val id: String) : Receiver {

    // Get instruments' stacks
    private fun getStacks(player: PlayerEntity): MutableList<ItemStack> {
        val list = mutableListOf<ItemStack>()
        for ( stack in player.inventory.main ) {
            if (stack.item.group == instrumentsGroup) {
                val hasTag = stack.tag!!.getString("MIDI Device") == id
                if ( hasTag ) { list.add(stack) }
            }
        }
        return list
    }

    override fun close() { println(" [HONKYTONES]: $id device has been closed.") }
    override fun send(msg: MidiMessage?, timeStamp: Long) {

        val client = MinecraftClient.getInstance();     val ply = client.player ?: return
        val stacks = getStacks(ply)

        val newMsg = msg as ShortMessage;       val channel = newMsg.channel

        val screen = client.currentScreen
        if (screen is Menu && client.isInSingleplayer) return

        for ( stack in stacks ) {

            val nbt = stack.tag!!
            val nbtChannel = nbt.getInt("MIDI Channel")
            val inst = stack.item as Instrument
            val sounds = inst.sounds["notes"]!!

            if ( nbt.getString("Action") != "Play" ) { return }

            if ( channel + 1 == nbtChannel ) {

                val index = inst.getIndexIfCenter(nbt, newMsg.data1)
                val sound = sounds[index] ?: return
                val volume = newMsg.data2 / 127f

                // 144 -> Note ON, 128 -> Note OFF
                if ( volume > 0 && newMsg.command == 144 ) {
                    sound.volume = volume * nbt.getFloat("Volume")
                    client.send { playSound(sound, ply) }
                } else if ( sound.isPlaying && inst.name != "drumset"
                    && newMsg.command == 128 ) {
                    client.send { stopSound(sound, sounds) }
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

        val id = Base.MOD_NAME

        sequenceMenuBind = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.$id.sequence",      InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,        "category.$id" )
        )

        sequenceResetBind = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.$id.restart",      InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,        "category.$id" )
        )

    }

}
