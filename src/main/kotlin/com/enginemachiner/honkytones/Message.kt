package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text

/** Puts the message in the chat, actionBar = false */
@Environment(EnvType.CLIENT)
fun printMessage( s: String ) { printMessage( s, false ) }

/** Puts the message in the chat, actionBar = false */
@Environment(EnvType.CLIENT)
fun printMessage( player: PlayerEntity, s: String ) { printMessage( player, s, false ) }

/** Puts the message in the chat */
@Environment(EnvType.CLIENT)
fun printMessage( s: String, actionBar: Boolean ) {
    val player = MinecraftClient.getInstance().player
    if (player == null) { println( Base.DEBUG_NAME + " $s" ); return }
    printMessage( player, s, actionBar )
}

/** Puts the message in the chat */
@Environment(EnvType.CLIENT)
fun printMessage( player: PlayerEntity, s: String, actionBar: Boolean ) {
    player.sendMessage( Text.of( "§3" + Base.DEBUG_NAME + " §f" + s ), actionBar )
}

@Environment(EnvType.CLIENT)
const val menuMessage = "You can't open the menu while holding two %item% at the same time!"