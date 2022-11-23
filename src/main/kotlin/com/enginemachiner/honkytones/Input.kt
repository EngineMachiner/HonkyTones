package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.InstrumentReceiver
import net.fabricmc.api.ClientModInitializer
import javax.sound.midi.*

class Input : ClientModInitializer {

    private val controllersMap = mutableMapOf<MidiDevice, MutableMap< List<Transmitter>, List<Receiver> > >()

    private fun checkDevices() {

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
                            println( Base.DEBUG_NAME + "MIDI device ${device.deviceInfo} " +
                                    "is unavailable.")
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