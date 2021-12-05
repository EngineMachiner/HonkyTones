package com.enginemachiner.honkytones

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import kotlin.reflect.full.createInstance


@Suppress("UNUSED")

val reference = mapOf(
    "C#" to "D_",   "D#" to "E_",   "F#" to "G_",
    "G#" to "A_",   "A#" to "B_"
)

val map = mapOf(
    Keyboard::class to "keyboard",       Organ::class to "organ",
    AcousticGuitar::class to "acousticguitar",        DrumSet::class to "drumset",
    ElectricGuitar::class to "electricguitar",       ElectricGuitarClean::class to "electricguitar-clean",
    Harp::class to "harp",       Viola::class to "viola",       Violin::class to "violin",
    Flute::class to "flute",       Oboe::class to "oboe",       Trombone::class to "trombone",
)

var doubleOctave = mutableListOf<String>()
var octave = mutableSetOf("C","D","E","F","G","A","B")
var setOf4 = setOf("B2-D3","E3_-G3_","G3-B3_","B3-D4")      // set of 4

val data = mutableMapOf( "organ" to setOf("C3-E3_","E3-G3","A3_-B3","B3") )

fun clone(s: Set<String>?): Set<String> {
    val newSet = mutableSetOf<String>()
    if (s != null) {
        for ( elements in s ) { newSet.add(elements) }
    }
    return newSet
}

private fun octaveBuilder( template: Set<String>?, range: Set<String> ): Set<String>{

    fun checkFlat(s: String, i: String): String {
        val s = s.filter { !it.isDigit() }
        var s2 = s + i
        if ( s.contains("_") ) { s2 = s[0] + i + s[1] }
        return s2
    }

    val newSet = mutableSetOf<String>()
    for ( r in range ) {
        for (t in template!!) {
            if (t.contains("-")) {

                // Only range
                val start = t.substringBefore("-")[1].toString()
                val end = t.substringAfter("-")[1].toString()
                var start2 = start;     var end2 = end

                if ( start.toInt() < end.toInt() && r == end ) {
                    start2 = (r.toInt() - 1).toString()
                }

                if ( end.toInt() > start.toInt() && r == start ) {
                    end2 = (r.toInt() + 1).toString()
                }

                if ( start == end ) {
                    start2 = r.toInt().toString()
                    end2 = r.toInt().toString()
                }

                newSet.add( t.replace( start, start2 ).replace( end, end2 ) )

            } else {

                val s = checkFlat(t, r)
                newSet.add(s)

            }
        }
    }

    return newSet
}

private fun moreData() {

    // Octave with flats
    var tempSet = mutableSetOf<String>()
    for (note in octave) {
        if ( note != "C" && note != "F" ) { tempSet.add(note + "_") }
        tempSet.add(note)
    }
    octave = tempSet

    // Back to data
    data["keyboard"] = octaveBuilder(octave, setOf("3","4","5","6"))
    tempSet = octaveBuilder(octave, setOf("1","2")) as MutableSet<String>
    for ( t in tempSet ) { doubleOctave.add( t.replace("1","").replace("2","") ) }

    // Custom set (16 notes)
    val drumSet = octaveBuilder(octave, setOf("4")) as MutableSet<String>
    val setAfter = mutableSetOf("C5","D5_","D5","E5_")
    for (n in setAfter) { drumSet.add(n) }
    data["drumset"] = drumSet

    data["electricguitar"] = octaveBuilder(octave, setOf("4"))
    data["acousticguitar"] = clone( data["organ"] )
    data["electricguitar-clean"] = clone( data["organ"] )
    data["harp"] = octaveBuilder(data["organ"], setOf("4"))
    data["viola"] = octaveBuilder(setOf4, setOf("3","6"))
    val preViolin = octaveBuilder( data["harp"], setOf("3") ) as MutableSet<String>
    preViolin.add( "C6" );      data["violin"] = preViolin
    data["flute"] = clone( data["harp"] )
    data["oboe"] = clone( data["harp"] )
    data["trombone"] = clone( data["harp"] )

}

object Base : ModInitializer {

    const val MOD_ID = "honkytones"

    private fun honkyTonesRegistry() {

        for ( n in map ) {
            val instance = n.key.createInstance()
            Registry.register(Registry.ITEM, Identifier(MOD_ID, n.value), instance)
        }

        // Create sound events

        val map2 = data
        for (pair in map2) {
            for (key in pair.value) {
                val path = "$MOD_ID:${pair.key}-${key.lowercase()}"
                val identifier = Identifier(path)
                val event = SoundEvent(identifier)
                Registry.register(Registry.SOUND_EVENT, identifier, event)
            }
        }

        for ( i in 1..9 ) {
            val identifier = Identifier("$MOD_ID:hit0$i")
            val event = SoundEvent(identifier)
            Registry.register(Registry.SOUND_EVENT, identifier, event)
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

fun serverToClients( from: String, to: String, radius: Float, func: ( b: PacketByteBuf ) -> PacketByteBuf ) {
    val server = ServerPlayNetworking.PlayChannelHandler {
            s: MinecraftServer, p: ServerPlayerEntity,
            _: ServerPlayNetworkHandler, buf: PacketByteBuf,
            _: PacketSender ->
        // Always re-create the buffer, never send it directly else it won't work
        val list = s.playerManager.playerList
        val newbuf = func(buf)
        s.send( ServerTask(s.ticks + 40) {
            for ( ply in list ) {
                if ( ply != p ) {
                    if ( radius == 0.0f ) {
                        ServerPlayNetworking.send(ply, Identifier(to), newbuf)
                    } else {
                        if ( ply.distanceTo(p) < radius ) {
                            ServerPlayNetworking.send(ply, Identifier(to), newbuf)
                        }
                    }
                }
            }
        } )
    }
    ServerPlayNetworking.registerGlobalReceiver( Identifier(from), server )
}

