package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
fun canNetwork(): Boolean { return client().networkHandler != null }

/** Register a common server receiver without filter function. */
fun registerSpecialServerReceiver(
    id: Identifier, bufWrite: ( sentBuf: PacketByteBuf, nextBuf: PacketByteBuf ) -> Unit
) {
    registerSpecialServerReceiver( id, bufWrite ) { _: ServerPlayerEntity, _: ServerPlayerEntity -> true }
}

/** Register a common server receiver with a filter function. */
fun registerSpecialServerReceiver(
    id: Identifier, bufWrite: ( sendBuf: PacketByteBuf, nextBuf: PacketByteBuf ) -> Unit,
    canNetwork: ( current: ServerPlayerEntity, sender: ServerPlayerEntity ) -> Boolean
) {

    ServerPlayNetworking.registerGlobalReceiver(id) {

        server: MinecraftServer, player: ServerPlayerEntity,
        _: ServerPlayNetworkHandler, buf: PacketByteBuf,
        _: PacketSender ->

        val nextBuf = PacketByteBufs.create();      bufWrite( buf, nextBuf )

        server.send( ServerTask( server.ticks ) {

            server.overworld.players.forEach {

                if ( !canNetwork( it, player ) ) return@forEach

                ServerPlayNetworking.send( it, id, nextBuf )

            }

        } )

    }

}