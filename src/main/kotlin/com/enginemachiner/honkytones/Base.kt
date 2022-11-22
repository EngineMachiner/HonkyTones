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
import com.enginemachiner.honkytones.items.instruments.Instrument.Companion.classesMap
import com.enginemachiner.honkytones.items.instruments.RangedEnchantment
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.StorageScreen
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantment
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import kotlin.reflect.full.createInstance

@Suppress("UNUSED")

// ItemGroup
object HTGroupIcon: Item( Settings() )

private val iconID = Identifier(Base.MOD_NAME, "itemgroup")
val honkyTonesGroup: ItemGroup = FabricItemGroupBuilder.create( iconID )!!
    .icon { HTGroupIcon.defaultStack }
    .build()

fun createDefaultItemSettings(): Item.Settings {
    return Item.Settings()
        .group( honkyTonesGroup )
        .maxCount( 1 )
}

// Sound.kt
val stackLists = mutableMapOf(
    "Instruments" to Instrument.stacks,
    "MusicalStorage" to MusicalStorage.stacks
)

@JvmField
val clientConfig = mutableMapOf<String, Any>()
val serverConfig = mutableMapOf<String, Any>()

class Base : ModInitializer, ClientModInitializer {

    init {

        buildConfigMaps()

        // Directory creation
        for ( dir in paths.values ) dir.mkdirs()

        // Temp files are deleted on start
        if ( !( clientConfig["keep_downloads"] as Boolean ) ) {
            for ( file in paths["streams"]!!.listFiles()!!) file.delete()
        }

    }

    override fun onInitialize() {
        NoteData.addSoundSets();     register();     tick();     networking()
        println("${MOD_NAME.uppercase()} has been initialized.")
    }

    override fun onInitializeClient() {
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
        val clientConfigFile = ClientConfigFile("client.txt")

        @JvmField
        val serverConfigFile = ServerConfigFile("server.txt")

        val paths = mutableMapOf(
            "streams" to RestrictedFile( "$MOD_NAME/streams/" ),
            "midis" to RestrictedFile( "$MOD_NAME/midi/" )
        )

        fun registerBlock(path: String, block: Block, itemSettings: Item.Settings ): Block? {
            val block = Registry.register(Registry.BLOCK, Identifier(MOD_NAME, path), block)
            val item = BlockItem(block, itemSettings)
            Registry.register(Registry.ITEM, Identifier(MOD_NAME, path), item)
            return block
        }

        val clientConfigKeys = mutableMapOf(

            Boolean::class to listOf(
                "keep_downloads", "keep_videos", "mobsParticles", "writeDeviceInfo",
                "playerParticles"
            ),

            Int::class to listOf("audio_quality")

        )

        val serverConfigKeys = mutableMapOf(

            Boolean::class to listOf(
                "debugMode", "mobsParticles", "playerParticles", "allowPushingPlayers"
            ),
            Int::class to listOf("mobsPlayingDelay")

        )

        fun buildConfigMaps() {

            // Client
            var boolKeys = clientConfigKeys[ Boolean::class ]!!

            for ( key in boolKeys ) {
                clientConfig[key] = clientConfigFile.properties.getProperty(key).toBoolean()
            }

            var intKeys = clientConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {

                clientConfig[key] = clientConfigFile.properties.getProperty(key).toInt()

                if ( key == "audio_quality" && ( clientConfig[key] as Int ) !in (0..10) ) {
                    clientConfig[key] = 5
                }

            }

            // Server
            boolKeys = serverConfigKeys[ Boolean::class ]!!
            for ( key in boolKeys ) {
                serverConfig[key] = serverConfigFile.properties.getProperty(key).toBoolean()
            }

            intKeys = serverConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {

                serverConfig[key] = serverConfigFile.properties.getProperty(key).toInt()

                if ( key == "mobsPlayingDelay" && ( serverConfig[key] as Int ) < 120 ) {
                    serverConfig[key] = 120
                }

            }


        }

        private fun registerItem( path: String, item: Item ) {
            Registry.register(Registry.ITEM, Identifier(MOD_NAME, path), item)
        }

        private fun registerSounds(path: String) {
            val id = Identifier(MOD_NAME, path);      val event = SoundEvent(id)
            Registry.register(Registry.SOUND_EVENT, id, event)
        }

        private fun registerEnchantment(path: String, enchantment: Enchantment) {
            Registry.register(Registry.ENCHANTMENT, Identifier(MOD_NAME, path), enchantment)
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
            registerItem( "itemgroup", HTGroupIcon )

            MusicPlayerBlock.register()

            registerItem("musicalstorage", MusicalStorage.itemToRegister)

            registerItem( "floppydisk", FloppyDisk() )
            registerItem( "digitalconsole", DigitalConsole() )
            registerItem( "screwdriver", Screwdriver() )

            // Register items
            for ( className in classesMap ) {
                registerItem( className.value, className.key.createInstance() )
            }
            Instrument.registerFuel()

            // Register sounds
            for (soundSet in soundsMap) {
                for (note in soundSet.value) {
                    registerSounds("${soundSet.key}-${note.lowercase()}")
                }
            }

            for ( i in 1..9 ) { registerSounds("hit0$i") }

            registerSounds("magic-c3-e3_")

            registerEnchantment("ranged", RangedEnchantment())

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

        private fun tick() {

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