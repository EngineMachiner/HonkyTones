package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.Timer.Companion.tickTimers
import com.enginemachiner.honkytones.MusicTheory.instrumentFiles
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayer
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerScreenHandler
import com.enginemachiner.honkytones.items.console.DigitalConsole
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.console.PickStackScreenHandler
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.*
import com.enginemachiner.honkytones.items.instruments.Instrument.Companion.hitSounds
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import com.enginemachiner.honkytones.sound.Sound
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class Init : ModInitializer, ClientModInitializer {

    override fun onInitialize() {

        MusicTheory.buildSoundData()

        register();     networking();       modPrint("Mod loaded.")

    }

    override fun onInitializeClient() {

        // Directory creation.
        for ( directory in directories.values ) directory.mkdirs()


        // Downloaded files are deleted on start by default.
        val keepDownloads = Config.client().keepDownloads

        if ( !keepDownloads ) deleteDownloads()


        MusicalStorage.registerRender();        registerKeyBindings()

        MIDI.configDevices()

    }

    companion object {

        init {

            ModID.init("HonkyTones");    ConfigFile.checkConfigDirectory()

        }

        // @Environment(EnvType.CLIENT)
        val directories = mutableMapOf(
            "streams" to ModFile( "$MOD_NAME/streams/" ),
            "midis" to ModFile( "$MOD_NAME/midi/" )
        )

        private fun register() {

            Register.item(ItemGroup) // Let's register item group first.

            Register.item( FloppyDisk() );       Register.item( DigitalConsole() )

            MusicPlayerBlock.register();        Register.item( MusicalStorage.registryItem )

            registerSounds()


            // Instruments.
            for ( kClass in Instrument.classes ) Register.item( kClass.createInstance() )


            Fuel.register();      Projectiles.register();      registerEnchantments()


            registerScreenHandlers();    registerCallbacks();   Screen.networking()

            registerTickEvents();       Commands.register();      ModParticles.register()

        }

        // @Environment(EnvType.CLIENT)
        fun registerKeyBindings() {

            Instrument.Companion.KeyBindings.register()

            DigitalConsoleScreen.registerKeyBindings()

        }

        private fun registerCallbacks() {

            ServerLifecycleEvents.SERVER_STOPPING.register { Config.SERVER.write() }


            if ( !isClient() ) return


            ClientLifecycleEvents.CLIENT_STOPPING.register { Config.CLIENT.write() }

            ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> MusicPlayer.onDisconnect() }

        }

        private fun registerSounds() {

            // Instruments sounds.
            for ( entry in instrumentFiles ) { for ( note in entry.value ) {

                val name1 = ModID.className( entry.key )

                val name2 = note.lowercase()

                Register.sound("$name1.$name2")

            } }

            for ( i in 1..9 ) hitSounds.add( Register.sound("hit$i") )

            Register.sound("magic.c3-e3_")

        }

        private fun registerEnchantments() {

            val enchantment = RangedEnchantment()

            Register.enchantment(enchantment);      Instrument.enchantments.add(enchantment)

        }

        private fun registerScreenHandlers() {

            DigitalConsoleScreenHandler.register();     StorageScreenHandler.register()
            MusicPlayerScreenHandler.register();        PickStackScreenHandler.register()

        }

        private fun registerTickEvents() {

            val serverTick = ServerTickEvents.StartWorldTick { tickTimers() }
            ServerTickEvents.START_WORLD_TICK.register(serverTick)

            if ( !isClient() ) return

            val clientTick = ClientTickEvents.StartWorldTick { tickTimers() }
            ClientTickEvents.START_WORLD_TICK.register(clientTick)

        }

        private fun networking() {

            NBT.networking();       Sound.networking();     Instrument.networking()

            HarmonyScreenInit.networking();                 MusicalStorage.networking()

            MusicPlayerBlockEntity.networking()

        }

    }

}

internal object Fuel {

    private fun register( kClass: KClass<*>, time: Int ) { Register.fuel( kClass, time ) }

    private val registerMap = mutableMapOf(
        MusicalStorage::class to 6000,          Harp::class to 2200,
        AcousticGuitar::class to 2200,          Banjo::class to 2200,
        Cello::class to 3000,                   Marimba::class to 4000,
        ElectricGuitar::class to 5500,          ElectricGuitarClean::class to 5500,
        Recorder::class to 600,                 Xylophone::class to 4000
    )

    fun register() { registerMap.forEach { (kClass, time) -> register(kClass, time) } }

}