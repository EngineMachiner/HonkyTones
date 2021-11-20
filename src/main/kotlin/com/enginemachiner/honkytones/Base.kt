package com.enginemachiner.honkytones

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import kotlin.reflect.full.createInstance

@Suppress("UNUSED")

var doubleOctave = mutableSetOf<String>()
var octave = mutableSetOf("C","D","E","F","G","A","B")

val data = mutableMapOf(
    "organ" to setOf("C4-E4_","E4-G4","A4_-B4"),    // set of 3
    "acousticguitar" to setOf("B3-D4","E4_-G4_","G4-B4_","B4-D5")   // set of 4
)

val map = mapOf(
    Keyboard::class to "keyboard",       Organ::class to "organ",
    AcousticGuitar::class to "acousticguitar",        DrumSet::class to "drumset",
    ElectricGuitar::class to "electricguitar",       ElectricGuitarClean::class to "electricguitar-clean",
    Harp::class to "harp",       Viola::class to "viola",       Violin::class to "violin",
    Flute::class to "flute",       Oboe::class to "oboe",       Trombone::class to "trombone",
)

fun clone(s: Set<String>?): Set<String> {
    val newS = mutableSetOf<String>()
    if (s != null) {
        for ( elements in s ) { newS.add(elements) }
    }
    return newS
}

private fun moreData() {

    // Complete set
    val preKeyboard = mutableSetOf<String>()
    for (note in octave) {
        if ( note != "C" && note != "F" ) { preKeyboard.add(note + "4_") }
        preKeyboard.add(note + "4")
    }
    octave = preKeyboard
    data["keyboard"] = preKeyboard

    doubleOctave = clone(preKeyboard) as MutableSet<String>
    for (note in octave) {
        val newNote = note.replace("4","5")
        doubleOctave.add(newNote)
    }

    // Custom set (16)
    val preDrumSet = mutableSetOf("C5","D5_","D5","E5_")
    for (n in octave) { preDrumSet.add(n) }
    data["drumset"] = preDrumSet

    data["electricguitar"] = clone( data["keyboard"] )
    data["electricguitar-clean"] = clone( data["organ"] )
    data["harp"] = clone( data["organ"] )
    data["viola"] = clone( data["acousticguitar"] )
    data["violin"] = clone( data["organ"] )
    data["flute"] = clone( data["organ"] )
    data["oboe"] = clone( data["organ"] )
    data["trombone"] = clone( data["organ"] )

}

object Base : ModInitializer {

    const val MOD_ID = "honkytones"

    private fun honkyTonesRegistry() {

        // Add instruments to groupItem

        for ( n in map ) {
            val instance = n.key.createInstance()
            Registry.register(Registry.ITEM, Identifier(MOD_ID, n.value), instance)
        }

        // Create sound events

        val map2 = data
        for (pair in map2) {
            for (key in pair.value) {
                val path = "honkytones:${pair.key}-${key.lowercase()}"
                val identifier = Identifier(path)
                val event = SoundEvent(identifier)
                Registry.register(Registry.SOUND_EVENT, identifier, event)
            }
        }

    }

    override fun onInitialize() {
        moreData()
        honkyTonesRegistry()
        println("$MOD_ID has been initialized.")
    }

}

// Networking

const val netID = Base.MOD_ID + "-networking-"

fun serverToClients( from: String, to: String, func: ( b: PacketByteBuf ) -> PacketByteBuf ) {
    val server = ServerPlayNetworking.PlayChannelHandler {
            s: MinecraftServer, p: ServerPlayerEntity,
            _: ServerPlayNetworkHandler, buf: PacketByteBuf,
            _: PacketSender ->
        // Always re-create the buffer, never send it directly else it won't work
        val list = s.playerManager.playerList
        val newbuf = func(buf)
        s.execute { for ( ply in list ) {
            if ( ply != p ) {
                ServerPlayNetworking.send(ply, Identifier(to), newbuf)
            }
        } }
    }
    ServerPlayNetworking.registerGlobalReceiver( Identifier(from), server )
}

