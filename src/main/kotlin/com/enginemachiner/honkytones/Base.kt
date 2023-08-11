package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.NoteData.soundsMap
import com.enginemachiner.honkytones.blocks.musicplayer.*
import com.enginemachiner.honkytones.items.Screwdriver
import com.enginemachiner.honkytones.items.console.DigitalConsole
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.console.PickStackScreenHandler
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.Instrument.Companion.classes
import com.enginemachiner.honkytones.items.instruments.RangedEnchantment
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.StorageScreen
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.reflect.full.createInstance

@Suppress("UNUSED")

// ItemGroup
lateinit var ITEM_GROUP: ItemGroup
private object ItemGroupData {

    val ITEM = Item( Item.Settings() )
    val ID = Identifier( Base.MOD_NAME, "itemgroup" )
    val REGISTRY: RegistryKey<ItemGroup> = RegistryKey.of( RegistryKeys.ITEM_GROUP, ID )!!
    val ITEM_GROUP: ItemGroup = FabricItemGroup.builder().displayName( Text.of("HonkyTones") )
        .icon { ITEM.defaultStack }
        .build()

}

fun createDefaultItemSettings(): Item.Settings { return Item.Settings().maxCount(1) }

val stackLists = mutableMapOf(
    "Instruments" to Instrument.stacks,
    "MusicalStorage" to MusicalStorage.stacks
)

@JvmField
@Environment(EnvType.CLIENT)
val clientConfig = mutableMapOf<String, Any>()

@JvmField
val serverConfig = mutableMapOf<String, Any>()

class Base : ModInitializer, ClientModInitializer {

    init { buildServerConfigMaps();     ITEM_GROUP = ItemGroupData.ITEM_GROUP }

    override fun onInitialize() {
        NoteData.buildSoundMap();   register();     registerTickEvents();     networking()
        println("${MOD_NAME.uppercase()} has been initialized.")
    }

    override fun onInitializeClient() {

        buildClientConfigMaps()

        // Directory creation
        for ( dir in paths.values ) dir.mkdirs()

        // Temp files are deleted on start
        val keepDownloads = clientConfig["keep_downloads"]
        if ( keepDownloads != null && !( keepDownloads as Boolean ) ) {
            for ( file in paths["streams"]!!.listFiles()!! ) file.delete()
        }

        StorageScreen.register()
        MusicPlayerScreen.register()
        DigitalConsoleScreen.register()
        MusicalStorage.registerRender()
        Commands.client()

    }

    companion object {

        const val MOD_NAME = "honkytones"
        val DEBUG_NAME = " [${MOD_NAME.uppercase()}]: "

        // Config files
        @JvmField
        @Environment(EnvType.CLIENT)
        val clientConfigFile = ClientConfigFile("client.txt")

        @JvmField
        val serverConfigFile = ServerConfigFile("server.txt")

        @Environment(EnvType.CLIENT)
        var paths = mutableMapOf(
            "streams" to RestrictedFile( "$MOD_NAME/streams/" ),
            "midis" to RestrictedFile( "$MOD_NAME/midi/" )
        )

        fun registerBlock(path: String, block: Block, itemSettings: Item.Settings ): Block? {

            val block = Registry.register(Registries.BLOCK, Identifier(MOD_NAME, path), block)
            val item = BlockItem(block, itemSettings)
            Registry.register(Registries.ITEM, Identifier(MOD_NAME, path), item)

            ItemGroupEvents.modifyEntriesEvent( ItemGroupData.REGISTRY ).register { it.add(block) }

            return block
        }

        @Environment(EnvType.CLIENT)
        val clientConfigKeys = mutableMapOf(

            Boolean::class to listOf(
                "keep_downloads", "keep_videos", "mobsParticles", "writeDeviceInfo",
                "playerParticles"
            ),

            Int::class to listOf( "audio_quality", "max_length" ),

            String::class to listOf( "ffmpegDir", "ytdl-Path" )

        )

        val serverConfigKeys = mutableMapOf(

            Boolean::class to listOf(
                "debugMode", "mobsParticles", "playerParticles", "allowPushingPlayers"
            ),
            Int::class to listOf("mobsPlayingDelay")

        )

        @Environment(EnvType.CLIENT)
        fun buildClientConfigMaps() {

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            val boolKeys = clientConfigKeys[ Boolean::class ]!!

            for ( key in boolKeys ) {
                clientConfig[key] = clientConfigFile.properties.getProperty(key).toBoolean()
            }

            val intKeys = clientConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {

                clientConfig[key] = clientConfigFile.properties.getProperty(key).toInt()

                if ( key == "audio_quality" && ( clientConfig[key] as Int ) !in (0..10) ) {
                    clientConfig[key] = 5
                }

                if ( key == "max_length" && ( clientConfig[key] as Int ) <= 0 ) {
                    clientConfig[key] = 1200
                }

            }

            val stringKeys = clientConfigKeys[ String::class ]!!
            for ( key in stringKeys ) {
                clientConfig[key] = clientConfigFile.properties.getProperty(key)
            }

        }

        fun buildServerConfigMaps() {

            val boolKeys = serverConfigKeys[ Boolean::class ]!!
            for ( key in boolKeys ) {
                serverConfig[key] = serverConfigFile.properties.getProperty(key).toBoolean()
            }

            val intKeys = serverConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {

                serverConfig[key] = serverConfigFile.properties.getProperty(key).toInt()

                if ( key == "mobsPlayingDelay" && ( serverConfig[key] as Int ) < 120 ) {
                    serverConfig[key] = 120
                }

            }


        }


        private fun registerItem( path: String, item: Item ) { registerItem( path, item, false ) }
        private fun registerItem( path: String, item: Item, skipEntry: Boolean ) {

            if ( !skipEntry ) ItemGroupEvents.modifyEntriesEvent( ItemGroupData.REGISTRY ).register { it.add(item) }

            Registry.register(Registries.ITEM, Identifier(MOD_NAME, path), item)

        }

        private fun registerSounds(path: String) {
            val id = Identifier(MOD_NAME, path);      val event = SoundEvent.of(id)
            Registry.register(Registries.SOUND_EVENT, id, event)
        }

        private fun registerEnchantment(path: String, enchantment: Enchantment) {
            Registry.register(Registries.ENCHANTMENT, Identifier(MOD_NAME, path), enchantment)
        }

        private fun registerCallbacks() {

            ServerLifecycleEvents.SERVER_STOPPING.register {
                serverConfigFile.updateProperties(serverConfig)
            }

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            ClientLifecycleEvents.CLIENT_STOPPING.register {
                clientConfigFile.updateProperties(clientConfig)
            }

        }

        private fun register() {

            // Register blocks first, then items
            // Important elements first

            // Register ItemGroup Icon
            registerItem( "itemgroup", ItemGroupData.ITEM, true )

            Registry.register( Registries.ITEM_GROUP, ItemGroupData.REGISTRY, ITEM_GROUP )

            MusicPlayerBlock.register()

            registerItem("musicalstorage", MusicalStorage.itemToRegister)

            registerItem( "floppydisk", FloppyDisk() )
            registerItem( "digitalconsole", DigitalConsole() )
            registerItem( "screwdriver", Screwdriver() )

            // Register items
            for ( className in classes ) registerItem( className.value, className.key.createInstance() )
            Instrument.registerFuel()

            // Register sounds
            for (soundSet in soundsMap) { for (note in soundSet.value) {
                registerSounds("${ soundSet.key }-${ note.lowercase() }")
            } }

            for ( i in 1..9 ) { registerSounds("hit0$i") }

            registerSounds("magic-c3-e3_")

            registerEnchantment( "ranged", RangedEnchantment() )

            MusicPlayerCompanion.register()
            NoteProjectileEntity.register()

            screenHandlerRegistry()

            registerCallbacks()

        }

        private fun screenHandlerRegistry() {

            DigitalConsoleScreenHandler.register()
            StorageScreenHandler.register()
            PickStackScreenHandler.register()
            MusicPlayerScreenHandler.register()

        }

        private fun registerTickEvents() {

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            val tick = ClientTickEvents.EndTick {
                val client = MinecraftClient.getInstance()
                if (client.player != null) Instrument.tick()
            }
            ClientTickEvents.END_CLIENT_TICK.register(tick)

        }

        private fun networking() {

            Sound.networking();     Instrument.networking();    MusicalStorage.networking()
            Projectiles.networking();       FloppyDisk.networking()

            MusicPlayerEntity.networking()
            MusicPlayerCompanion.networking()

            Network.register()

        }

    }

}