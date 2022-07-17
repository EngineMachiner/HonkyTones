package com.enginemachiner.honkytones

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import kotlin.reflect.full.createInstance

@Suppress("UNUSED")

// User commands
val defCommands: MutableMap<String, Any> = mutableMapOf(
    "debugMode" to false,
    "mobsPlayingDelay" to 120
)

val commands: MutableMap<String, Any> = mutableMapOf(
    "debugMode" to false,
    "mobsPlayingDelay" to 120
)

val notes = mutableSetOf("C","D","E","F","G","A","B")
val octave = mutableSetOf<String>()
val twoOctaves = mutableListOf<String>()
var wholeNoteSet = setOf<String>()

val sharpsMap = mapOf(
    "C#" to "D_",   "D#" to "E_",   "F#" to "G_",
    "G#" to "A_",   "A#" to "B_"
)

val classesMap = mapOf(
    DrumSet::class to "drumset",
    Keyboard::class to "keyboard",       Organ::class to "organ",
    AcousticGuitar::class to "acousticguitar", ElectricGuitar::class to "electricguitar",
    ElectricGuitarClean::class to "electricguitar-clean",
    Harp::class to "harp",       Viola::class to "viola",       Violin::class to "violin",
    Trombone::class to "trombone",  Flute::class to "flute",       Oboe::class to "oboe",
)

// Adds a set elements to another set
fun <T> clone(set: Set<T>?): Set<T> {
    val newSet = mutableSetOf<T>()
    for ( element in set!!) { newSet.add(element) }
    return newSet
}

private fun builder(template: Set<String>?, range: Set<Int>): Set<String>{

    val newSet = mutableSetOf<String>()
    for ( r in range ) {
        val r = r.toString()
        for (t in template!!) {
            if (t.contains("-")) {

                val first = template.first().substringAfter("-")[1].toString()

                // Sort the pair values according to the range given
                val start = t.substringBefore("-")[1].toString()
                val end = t.substringAfter("-")[1].toString()
                var start2 = r;     var end2 = r

                if ( start.toInt() < end.toInt() ) {
                    start2 = (r.toInt() - 1).toString()
                }

                if ( first.toInt() < end.toInt() ) {
                    val dif = end.toInt() - first.toInt()
                    start2 = (start2.toInt() + dif).toString()
                    end2 = (end2.toInt() + dif).toString()
                }

                newSet.add( t.replace( start, start2 ).replace( end, end2 ) )

            } else {

                val s = t.filter { !it.isDigit() };     var s2 = s + r
                if ( s.contains("_") ) { s2 = s[0] + r + s[1] }
                newSet.add(s2)

            }
        }
    }

    return newSet
}

val soundsMap = mutableMapOf< String, Set<String> >()
private fun addSoundSets() {

    // Build a single octave with all notes
    for (note in notes) {
        if (note != "C" && note != "F") {
            octave.add(note + "_")
        }
        octave.add(note)
    }

    // A two octaves list to get relative semitone positions
    val tempTwo = builder(octave, setOf(1, 2))
    for (t in tempTwo) {
        val s = t.replace("1", "").replace("2", "")
        twoOctaves.add(s)
    }

    // And a complete whole note set for midi messages references
    val wholeRange = mutableSetOf<Int>()
    for (i in -1..8) wholeRange.add(i)
    wholeNoteSet = builder(octave, wholeRange)

    // Start adding the set with the sound structure to the map

    val soundsSet4 = setOf("B2-D3", "E3_-G3_", "G3-B3_", "B3-D4")

    // Drum set setup
    val drumSet = builder(octave, setOf(2)) as MutableSet<String>
    val tempDrumSet = mutableSetOf("C3", "D3_", "D3", "E3_")
    for (n in tempDrumSet) {
        drumSet.add(n)
    }
    soundsMap["drumset"] = drumSet

    soundsMap["organ"] = setOf("C3-E3_", "E3-G3", "A3_-B3", "B3")
    soundsMap["harp"] = builder(soundsMap["organ"], setOf(4))

    // Violin setup
    val tempViolin = builder(soundsMap["harp"], setOf(3)) as MutableSet<String>
    tempViolin.add("C6"); soundsMap["violin"] = tempViolin

    soundsMap["keyboard"] = builder(octave, setOf(3, 4, 5, 6))
    soundsMap["electricguitar"] = builder(octave, setOf(4))
    soundsMap["acousticguitar"] = clone(soundsMap["organ"])
    soundsMap["electricguitar-clean"] = clone(soundsMap["organ"])
    soundsMap["viola"] = builder(soundsSet4, setOf(3, 6))
    soundsMap["flute"] = clone( soundsMap["harp"] )
    soundsMap["oboe"] = clone( soundsMap["harp"] )
    soundsMap["trombone"] = clone( soundsMap["harp"] )

}

object Base : ModInitializer {

    const val MOD_NAME = "honkytones"

    private fun registerSounds(path: String) {
        val id = Identifier(path);      val event = SoundEvent(id)
        Registry.register(Registry.SOUND_EVENT, id, event)
    }

    private fun register() {

        // Register ItemGroup Icon
        Registry.register(Registry.ITEM, Identifier(MOD_NAME, "itemgroup"), HTGroupIcon)

        // Register sound events
        for (soundSet in soundsMap) {
            for (note in soundSet.value) {
                registerSounds("$MOD_NAME:${soundSet.key}-${note.lowercase()}")
            }
        }

        // Register hit sounds
        for ( i in 1..9 ) { registerSounds("$MOD_NAME:hit0$i") }

        // Register magic sounds
        registerSounds("$MOD_NAME:magic-c3-e3_")

        // Register all ITEMs
        for ( className in classesMap ) {
            val id = Identifier(MOD_NAME, className.value)
            val instance = className.key.createInstance()
            Registry.register(Registry.ITEM, id, instance)
        }

        // Commands
        CommandRegistrationCallback.EVENT.register {
                dispatcher: CommandDispatcher<ServerCommandSource>,
                _: CommandRegistryAccess,
                _: CommandManager.RegistrationEnvironment ->

            val l1 = CommandManager.literal("honkytones")
            var l2 = CommandManager.literal("debugger")
            val argBool = CommandManager.argument( "bool", BoolArgumentType.bool() )

            var command = l1.then( l2.then( argBool.executes {
                val b = BoolArgumentType.getBool(it, "bool")
                commands["debugMode"] = b
                0
            } ) )
            dispatcher.register(command)

            l2 = CommandManager.literal("mob_playing_delay")
            val argInt = CommandManager.argument( "int", IntegerArgumentType.integer() )
            val min = defCommands["mobsPlayingDelay"] as Int
            command = l1.then( l2.then( argInt.executes {

                val i = IntegerArgumentType.getInteger(it, "int")
                val error = "Number has to be minimum 120"

                if (i >= min) { commands["mobPlayingTickDelay"] = i }
                else it.source.player!!.sendMessage( Text.of(error), false )

                0

            } ) )
            dispatcher.register(command)

        }

    }

    override fun onInitialize() {
        addSoundSets();     register()
        println("$MOD_NAME has been initialized.")
    }

}

// Sender -> Server -> Receiver networking
// This basically declares the server network method (nothing else)
fun serverToClients(
    funcName: String, radius: Float, func: ( b: PacketByteBuf ) -> PacketByteBuf ) {

    val net = ServerPlayNetworking.PlayChannelHandler {
            server: MinecraftServer, sender: ServerPlayerEntity,
            _: ServerPlayNetworkHandler, buf: PacketByteBuf,
            _: PacketSender ->

        fun send(receiver: ServerPlayerEntity, buf: PacketByteBuf) {
            ServerPlayNetworking.send(receiver, Identifier(funcName), buf)
        }

        // Always re-create the buffer, never send it directly else it won't work
        val list = server.playerManager.playerList;      val newbuf = func(buf)

        server.send( ServerTask(server.ticks + 18) {
            for ( ply in list ) {
                if ( ply != sender ) {
                    if ( radius == 0f ) { send(ply, newbuf) }
                    else if ( ply.distanceTo(sender) < radius ) { send(ply, newbuf) }
                    if ( commands["debugMode"]!! as Boolean ) {
                        println("$sender networking sound to $ply")
                    }
                }
            }
        } )

    }
    ServerPlayNetworking.registerGlobalReceiver( Identifier(funcName), net )

}

