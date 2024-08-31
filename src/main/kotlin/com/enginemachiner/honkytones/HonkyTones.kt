package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.Timer.Companion.tickTimers
import com.enginemachiner.honkytones.MusicTheory.noteMap
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlock
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerScreenHandler
import com.enginemachiner.honkytones.items.FloppyDisk
import com.enginemachiner.honkytones.items.console.DigitalConsole
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.console.PickStackScreenHandler
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import com.enginemachiner.honkytones.items.instruments.InstrumentSound
import com.enginemachiner.honkytones.items.instruments.RangedEnchantment
import com.enginemachiner.honkytones.items.instruments.SFX
import com.enginemachiner.honkytones.items.music_player.RadioItem
import com.enginemachiner.honkytones.items.music_player.Remote
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import kotlin.reflect.full.createInstance

const val SOUND_MIN_DISTANCE = 16f

fun modSound(path: String): SoundEvent? { val id = modID(path);     return Registries.SOUND_EVENT.get(id) }

class HonkyTones : ModInitializer {

    override fun onInitialize() {

        ModID.init("HonkyTones");       ConfigFile.checkDirectory()

        MusicTheory.build()


        register();     networking();       modPrint("Mod loaded.")

    }

    private companion object {

        fun register() {

            registerSounds()

            Register.group( ModItemGroup.itemGroup )
            Register.item( ModItemGroup.item )
            
            MusicPlayerBlock.register();         Register.item( MusicalStorage.registryItem )




            Register.item( FloppyDisk.registeredItem )

            RadioItem.registerItem()

            Register.item( Remote() );      Register.item( DigitalConsole() )

            registerInstruments()


            Fuel.register();      Projectiles.register()

            Commands.register();      ModParticles.register()


            registerScreenHandlers();    registerCallbacks();       registerEnchantments()

            registerTickEvents()

        }

        fun registerInstruments() {

            for ( kClass in InstrumentItem.classes ) Register.item( kClass.createInstance() )

            SFX.registeredItem = modItem( SFX::class )

        }

        fun registerCallbacks() {

            ServerLifecycleEvents.SERVER_STOPPING.register { Config.SERVER.write() }

        }

        fun registerSounds() {

            RadioItem.registerSound()

            // Instruments sounds.
            for ( entry in noteMap ) { for ( note in entry.value ) {

                val name1 = ModID.className( entry.key )

                val name2 = note.lowercase()

                Register.sound("$name1.$name2")

            } }

            InstrumentItem.registerHitSounds()

            Register.sound("magic.c3-e3_")

        }

        fun registerEnchantments() {

            val enchantment = RangedEnchantment()

            RangedEnchantment.registered = Register.enchantment(enchantment)

            InstrumentItem.enchantments.add(enchantment)

        }

        fun registerScreenHandlers() {

            DigitalConsoleScreenHandler.register();     StorageScreenHandler.register()
            MusicPlayerScreenHandler.register();        PickStackScreenHandler.register()

        }

        fun registerTickEvents() {

            val serverTick = ServerTickEvents.StartWorldTick { tickTimers() }

            ServerTickEvents.START_WORLD_TICK.register(serverTick)

        }

        fun networking() {

            NBT.networking();       InstrumentItem.networking();        InstrumentSound.networking()

            ScreenRefresher.networking();         MusicPlayerBlockEntity.networking()

            registerCloseScreenReceiver()

        }

    }

}