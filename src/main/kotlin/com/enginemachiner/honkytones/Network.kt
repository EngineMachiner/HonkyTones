package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerCompanion
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import org.apache.commons.validator.routines.UrlValidator
import kotlin.math.pow

/*
 Storing item stacks and networking them from client
 is impossible because I think they update a lot and there's not one unique stack
*/

object Network {

    /**
    Sender -> Server -> Clients (not including Sender)
    This basically declares the server network method (nothing else)
     */
    fun registerServerToClientsHandler( clientHandlerIdFrom: String, clientHandlerIdTo: String,
                                        radius: Float, ticksAhead: Int,
                                        packetReader: (b: PacketByteBuf) -> PacketByteBuf ) {
        registerServerToClientsHandler( clientHandlerIdFrom, clientHandlerIdTo, radius, ticksAhead,
            true, packetReader )
    }

    fun registerServerToClientsHandler( clientHandlerId: String, radius: Float, ticksAhead: Int,
                                        packetReader: (b: PacketByteBuf) -> PacketByteBuf ) {
        registerServerToClientsHandler( clientHandlerId, radius, ticksAhead,
            true, packetReader )
    }

    fun registerServerToClientsHandler( clientHandlerIdFrom: String,
                                        radius: Float, ticksAhead: Int, filter: Boolean,
                                        packetReader: (b: PacketByteBuf) -> PacketByteBuf
    ) {
        registerServerToClientsHandler( clientHandlerIdFrom, clientHandlerIdFrom,
            radius, ticksAhead, filter, packetReader )
    }

    fun registerServerToClientsHandler( clientHandlerIdFrom: String, clientHandlerIdTo: String,
                                        radius: Float, ticksAhead: Int, filter: Boolean,
                                        packetReader: (b: PacketByteBuf) -> PacketByteBuf
    ) {

        val idFrom = Identifier( Base.MOD_NAME, clientHandlerIdFrom )
        val idTo = Identifier( Base.MOD_NAME, clientHandlerIdTo )

        fun send( receiver: ServerPlayerEntity, buf: PacketByteBuf ) {
            ServerPlayNetworking.send( receiver, idTo, buf )
        }

        ServerPlayNetworking.registerGlobalReceiver(idFrom) {

                server: MinecraftServer, sender: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf,
                _: PacketSender ->

            // Always re-create the buffer, never send it directly else it won't work
            val list = server.playerManager.playerList;      val newbuf = packetReader(buf)
            var radiusEntity = sender as Entity

            // Special cases
            if ( idFrom.path == "play_midi" ) {
                radiusEntity = MusicPlayerCompanion.entities.find {
                    PacketByteBufs.copy(newbuf).readString() == it.uuidAsString
                } ?: return@registerGlobalReceiver
            }

            server.send( ServerTask(server.ticks + ticksAhead) {

                if ( !filter ) return@ServerTask

                for ( ply in list ) {
                    if ( ply != sender ) {

                        if ( radius == 0f ) send(ply, newbuf)
                        else if ( ply.squaredDistanceTo( radiusEntity ) < radius.pow(2) ) send(ply, newbuf)

                        if ( serverConfig["debugMode"]!! as Boolean ) {
                            println( Base.DEBUG_NAME + "$sender networking to $ply with id: $clientHandlerIdFrom" )
                        }

                    }
                }

            } )

        }

    }

    fun isValidUrl(s: String): Boolean {
        var s = s
        val b = !s.startsWith("http://") && !s.startsWith("https://")
        if (b) s = "http://$s";         return UrlValidator().isValid(s)
    }

    @Environment(EnvType.CLIENT)
    fun canNetwork(): Boolean {
        val client = MinecraftClient.getInstance()
        return client.networkHandler != null
    }

    @Environment(EnvType.CLIENT)
    fun isOnline(): Boolean {
        val client = MinecraftClient.getInstance()
        val server = client.server
        val b = server == null || server.isRemote
        return b && canNetwork()
    }

    @Environment(EnvType.CLIENT)
    fun sendNbtToServer( nbt: NbtCompound ) {
        val id = Identifier( Base.MOD_NAME, "update_nbt" )
        val buf = PacketByteBufs.create();      buf.writeNbt(nbt)
        ClientPlayNetworking.send(id, buf)
    }

    fun register() {

        val id = Identifier(Base.MOD_NAME,"update_nbt" )

        ServerPlayNetworking.registerGlobalReceiver(id) {
                server: MinecraftServer, player: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf,
                _: PacketSender ->

            val nbt = buf.readNbt()!!;      val hand = nbt.getInt("hand")

            if ( hand == -1 ) return@registerGlobalReceiver

            server.send ( ServerTask(server.ticks + 1) {

                val stack = player.getStackInHand( Hand.values()[hand] )
                val currentNbt = stack.nbt ?: return@ServerTask

                // Put display name
                if ( nbt.contains("display") ) {
                    currentNbt.put( "display", nbt.getCompound("display") )
                    nbt.remove("display")
                } else if ( nbt.contains("removeName") ) {
                    stack.removeCustomName();   nbt.remove("removeName")
                }

                currentNbt.put(Base.MOD_NAME, nbt)

            } )

        }

    }

}