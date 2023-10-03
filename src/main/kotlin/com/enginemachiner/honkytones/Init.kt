package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.MusicTheory.instrumentFiles
import com.enginemachiner.honkytones.Timer.Companion.tickTimers
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerScreen
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerScreenHandler
import com.enginemachiner.honkytones.items.console.DigitalConsole
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.console.PickStackScreenHandler
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.Instrument.Companion.hitSounds
import com.enginemachiner.honkytones.items.instruments.RangedEnchantment
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.StorageScreen
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import com.enginemachiner.honkytones.sound.Sound
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.Block
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import kotlin.reflect.full.createInstance

// TODO: Check shadow / inheritance instead of double casting for mixin objects.
// TODO: Check inherited methods on vanilla classes.
// TODO: Add advancements.

class Init : ModInitializer, ClientModInitializer {

    override fun onInitialize() {

        readServerConfig();     MusicTheory.buildSoundData();      register()

        networking();           modPrint("Mod loaded.")

    }

    override fun onInitializeClient() {

        readClientConfig()

        // Directory creation.
        for ( directory in directories.values ) directory.mkdirs()

        // Downloaded files are deleted on start by default.
        val keepDownloads = clientConfig["keep_downloads"] as Boolean

        if ( !keepDownloads ) deleteDownloads()

        StorageScreen.register();               MusicPlayerScreen.register()
        DigitalConsoleScreen.register();        MusicalStorage.registerRender()
        Commands.client()

        registerKeyBindings();                  Midi.configDevices()

    }

    companion object {

        init { ConfigFile.checkConfigDirectory() }

        const val MOD_NAME = "honkytones";      val chatTitle = "§3 [${ MOD_NAME.uppercase() }]: §f"

        @Environment(EnvType.CLIENT)
        var directories = mutableMapOf(
            "streams" to ModFile( "$MOD_NAME/streams/" ),
            "midis" to ModFile( "$MOD_NAME/midi/" )
        )

        @Environment(EnvType.CLIENT)
        fun registerKeyBindings() {

            Instrument.Companion.KeyBindings.register()

            DigitalConsoleScreen.registerKeyBindings()

        }

        private fun addToGroup(item: Item) {

            if ( item == ItemGroup ) return

            ItemGroupEvents.modifyEntriesEvent( ItemGroup.registry )
                .register { it.add(item) }

        }

        fun registerBlock( block: Block, itemSettings: Item.Settings ): Block {

            val s = ( block as ModID ).className().replace( "_block", "" )

            val id = modID(s);       val block = Registry.register( Registries.BLOCK, id, block )

            val item = BlockItem( block, itemSettings );    Registry.register( Registries.ITEM, id, item )

            addToGroup(item);   return block

        }

        private fun registerItem(item: Item) {

            val id = ( item as ModID ).classID()

            Registry.register( Registries.ITEM, id, item )

            addToGroup(item)

        }

        private fun registerSound(path: String): SoundEvent {

            val id = modID(path);      val event = SoundEvent.of(id)

            return Registry.register( Registries.SOUND_EVENT, id, event )

        }

        private fun registerEnchantment( enchantment: Enchantment ) {

            val path = ( enchantment as ModID ).className()
                .replace("_enchantment", "")

            Registry.register( Registries.ENCHANTMENT, modID(path), enchantment )

        }

        private fun registerCallbacks() {

            ServerLifecycleEvents.SERVER_STOPPING.register {
                serverConfigFile.updateProperties(serverConfig)
            }

            if ( !isClient() ) return

            ClientLifecycleEvents.CLIENT_STOPPING.register {
                clientConfigFile.updateProperties(clientConfig)
            }

        }

        private fun register() {

            // Item group first.
            Registry.register( Registries.ITEM_GROUP, ItemGroup.registry, itemGroup )
            
            registerItem(ItemGroup)

            registerItem( FloppyDisk() );       registerItem( DigitalConsole() )

            MusicPlayerBlock.register();        registerItem( MusicalStorage.registryItem )

            // Register instruments sounds.
            for ( entry in instrumentFiles ) { for ( note in entry.value ) {

                val name1 = ModID.className( entry.key );   val name2 = note.lowercase()

                registerSound("$name1.$name2")

            } }

            // Register instruments.
            for ( kclass in Instrument.classes ) registerItem( kclass.createInstance() )

            Fuel.register();      NoteProjectileEntity.register()

            for ( i in 1..9 ) hitSounds.add( registerSound("hit$i") )

            registerSound("magic.c3-e3_")

            registerEnchantment( RangedEnchantment() )

            registerScreenHandlers();    registerCallbacks();   Screen.networking()

            registerTickEvents()

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

            Projectiles.networking();               MusicalStorage.networking()

            MusicPlayerBlockEntity.networking();    PickStackScreenHandler.networking()

        }

    }

}