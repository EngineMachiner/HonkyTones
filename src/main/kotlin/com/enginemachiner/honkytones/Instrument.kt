package com.enginemachiner.honkytones

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.item.Item
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import kotlin.math.abs

private val identifier = Identifier(Base.MOD_ID, "itemgroup")
private val group = FabricItemGroupBuilder.create(identifier)!!
    .build()

private val setting = Item.Settings()
    .group( group )
    .maxCount( 1 )

// Equal temperament
const val semitone = 1f / 12f

open class Instrument : Item(setting) {

    val instrumentName = map[this::class]
    private var soundPathHint = ""

    // Use sound

    private var pitch = 1f
    private fun networking() {

        var soundInstanceN = getSoundInstance("")

        serverToClients( netID + "soundevent", netID + "soundevent-clients" ) {
                buf: PacketByteBuf -> val newbuf = PacketByteBufs.create()
            newbuf.writeString(buf.readString());   newbuf.writeBlockPos(buf.readBlockPos())
            newbuf.writeFloat(buf.readFloat())
            newbuf
        }

        var clientH = ClientPlayNetworking.PlayChannelHandler {
                client: MinecraftClient, _: ClientPlayNetworkHandler, packet: PacketByteBuf,
                _: PacketSender -> val id = packet.readString();    val pitch = packet.readFloat()
                val pos = packet.readBlockPos()
                val instance = getSoundInstance(id);    instance.movePos = pos
                client.execute {
                    soundInstanceN = getSoundInstance(id)
                    soundInstanceN.pitch = pitch
                    client.soundManager.play(soundInstanceN)
                }
        }
        ClientPlayNetworking.registerGlobalReceiver( Identifier(netID + "soundevent-clients"), clientH )

        serverToClients( netID + "soundevent-stop", netID + "soundevent-stop-clients") { PacketByteBufs.empty() }

        clientH = ClientPlayNetworking.PlayChannelHandler {
                client: MinecraftClient, _: ClientPlayNetworkHandler, _: PacketByteBuf,
                _: PacketSender -> client.execute { soundInstanceN.stop() }
        }
        ClientPlayNetworking.registerGlobalReceiver( Identifier(netID + "soundevent-stop-clients"), clientH )

        // The KeyBinding declaration and registration are commonly executed here statically
        // Event registration will be executed inside this method
        var switch = false;    var id = "";     var soundInstanceC = getSoundInstance(id)
        val tick = ClientTickEvents.EndTick {
                client: MinecraftClient ->

            val keyfactory = HonkyTonesClientEntrypoint.fabricKeyBuilder
            val press = keyfactory.isPressed
            val player = client.player

            if (player != null) {

                val item = player.mainHandStack.item

                if ( press && keyfactory.wasPressed() && item.group == group ) {

                    val instrument = item as Instrument
                    soundPathHint = "honkytones:${instrument.instrumentName}-"
                    id = getNote(instrument, "E4");       switch = true
                    println(id)
                    val buf = PacketByteBufs.create()
                    buf.writeString(id);        buf.writeFloat(pitch)
                    buf.writeBlockPos(client.player!!.blockPos)

                    soundInstanceC = getSoundInstance(id)
                    soundInstanceC.player = player
                    soundInstanceC.pitch = pitch

                    client.soundManager.play(soundInstanceC)
                    ClientPlayNetworking.send(Identifier(netID + "soundevent"), buf)

                }

                if ( switch && id.isNotEmpty() && !press ) {
                    val buf = PacketByteBufs.empty();       switch = false
                    ClientPlayNetworking.send(Identifier(netID + "soundevent-stop"), buf)
                    soundInstanceC.stop()
                }

            }

        }
        ClientTickEvents.END_CLIENT_TICK.register(tick)

    }

    private fun randomKey(inst: Instrument): String {
        val name = inst.instrumentName
        if ( data.containsKey(name) ) {
            val key = data[name]!!.random().lowercase()
            return soundPathHint.plus(key)
        }
        println(" [HONKYTONES]: ERROR: Missing sound for $name!")
        return "BLOCK_ANVIL_LAND"
    }

    private fun getNote(inst: Instrument, s: String): String {

        val debug = true
        fun error(): String { return "" }
        fun path(s: Any): String { return soundPathHint.plus(s).lowercase() }

        pitch = 1f // default
        val i = s.filter { it.isDigit() }
            .toInt()

        val name = inst.instrumentName

        for ( note in data[name]!! ) {

            // 1. case -> Literal
            if (note == "$s$i") { return path(note) }

            // 2. case -> Pitch

            // Build range
            val noteRangeI = note.substringBefore("-")
            val noteRangeE = note.substringAfter("-")

            // Default pitch
            if (s == noteRangeI) { return path(note) }

            if (noteRangeI != noteRangeE) {

                // Range between notes pitch (one way up range)
                var index = 0f
                var index2 = 0f
                var index3 = 0f

                // Start
                for (noteDO in doubleOctave) {
                    val a = noteDO.filter { !it.isDigit() }
                    val b = noteRangeI.filter { !it.isDigit() }
                    index = doubleOctave.indexOf(noteDO).toFloat() + 1
                    if (a == b) {
                        break
                    }
                }

                // End
                for (noteDO in doubleOctave) {
                    val a = noteDO.filter { !it.isDigit() }
                    val b = noteRangeE.filter { !it.isDigit() }
                    index3 = doubleOctave.indexOf(noteDO).toFloat() + 1
                    if (a == b && index3 >= index) { break }
                }

                // Max pitch case (not border note)
                if (s == noteRangeE) {
                    pitch = 1 + ( semitone * abs(index3 - index) * 0.75f )
                    return path(note)
                }

                // Between
                for (noteDO in doubleOctave) {
                    val a = noteDO.filter { !it.isDigit() }
                    val b = s.filter { !it.isDigit() }
                    index2 = doubleOctave.indexOf(noteDO).toFloat() + 1
                    if (a == b && index3 >= index2 && index2 >= index) { break }
                }

                if ( index2 <= index3 ) {
                    if (debug) {
                        println("$index $index2 $index3")
                        println("dist is ${abs(index3 - index) - abs(index3 - index2)}")
                        println(s.filter { !it.isDigit() })
                        println("$noteRangeI, $noteRangeE, $s, $doubleOctave")
                    }

                    if (index != 0f && index2 != 0f && index3 != 0f) {
                        // distance End and Between
                        var dist = abs(index3 - index)
                        dist -= abs(index3 - index2)
                        pitch = 1 + (semitone * dist) * 0.75f
                        return path(note)
                    }
                }

                if (abs(index2) <= index) {
                    pitch = 1 + ( semitone * abs( index - abs(index2) ) ) * 0.75f
                }

                if (index3 <= abs(index2)) {
                    pitch = 1 + ( semitone * abs( index3 - abs(index2) ) ) * 0.75f
                }

            }

        }

        println(" [HONKYTONES]: ERROR: Note $s$i for instrument $name not found!")
        return error()

    }

    init { networking() }

}

class Keyboard : Instrument();      class Organ : Instrument()
class DrumSet : Instrument();       class AcousticGuitar : Instrument()
class ElectricGuitar : Instrument();        class ElectricGuitarClean : Instrument()
class Harp : Instrument();        class Viola : Instrument()
class Violin : Instrument();        class Flute : Instrument()
class Oboe : Instrument();        class Trombone : Instrument()