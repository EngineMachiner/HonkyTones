package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.MOD_NAME
import com.enginemachiner.harmony.ModFile
import com.enginemachiner.harmony.Timer.Companion.tickTimers
import com.enginemachiner.harmony.modItem
import com.enginemachiner.honkytones.client.blocks.music_player.MusicPlayer
import com.enginemachiner.honkytones.client.blocks.music_player.MusicPlayerBlock
import com.enginemachiner.honkytones.client.blocks.music_player.MusicPlayerEntity
import com.enginemachiner.honkytones.client.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.client.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.client.items.instruments.Instrument
import com.enginemachiner.honkytones.client.items.instruments.SFX
import com.enginemachiner.honkytones.client.items.music_player.Radio
import com.enginemachiner.honkytones.client.items.storage.MusicalStorage
import com.enginemachiner.honkytones.client.sound.InstrumentSound
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

// TODO: CyclingButtonWidget.

class HonkyTones : ClientModInitializer {

    override fun onInitializeClient() {

        // Directory creation.
        for ( directory in directories.values ) directory.mkdirs()


        // Downloaded files are deleted on start by default.
        val keepDownloads = Config.client().keepDownloads

        if ( !keepDownloads ) deleteDownloads()


        MIDI.setup();       Commands.register()

        ModParticles.register();        Projectiles.register()


        createSoundModels()

        DigitalConsoleScreen.register()

        MusicalStorage.register();      MusicalStorage.registerRender()

        MusicPlayerBlock.register();        MusicPlayerEntity.register()

        registerColorProviders();       networking()


        registerKeyBindings();           registerCallbacks()

        registerTickEvents()


    }

    private fun registerColorProviders() {

        FloppyDisk.registerColorProvider()
        Radio.registerColorProvider()
        SFX.registerColorProvider()

    }

    private fun createSoundModels() {

        val model = Instrument.Sounds::Model

        val classes = InstrumentItem.classes

        for ( kClass in classes ) {

            val instrument = modItem(kClass) as InstrumentItem

            Instrument.Sounds.models[instrument] = model(instrument)

        }

    }

    companion object {

        val directories = mutableMapOf(
            "streams" to ModFile( "$MOD_NAME/streams/" ),
            "midis" to ModFile( "$MOD_NAME/midi/" )
        )

        fun registerKeyBindings() {

            Instrument.KeyBindings.register()

            DigitalConsoleScreen.Companion.KeyBindings.register()

        }

        private fun registerCallbacks() {

            ClientLifecycleEvents.CLIENT_STOPPING.register { Config.CLIENT.write() }

            ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> MusicPlayer.onDisconnect() }

        }

        private fun registerTickEvents() {

            val clientTick = ClientTickEvents.StartWorldTick {

                tickTimers();    Instrument.Tick.onKey()

            }

            ClientTickEvents.START_WORLD_TICK.register(clientTick)

        }

        private fun networking() {

            InstrumentSound.networking();         FloppyDisk.networking()

            Instrument.networking();            Silencer.networking()

            MusicPlayerBlock.networking();          DigitalConsoleScreen.networking()

            Radio.networking()

        }

    }

}

object Projectiles {

    fun register() { NoteEntity.register() }

}