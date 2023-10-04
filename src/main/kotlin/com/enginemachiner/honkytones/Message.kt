package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.chatTitle
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text

/** Warns player, not using the action bar. */
@Environment(EnvType.CLIENT)
fun warnUser(s: String) { warnPlayer( s, false ) }

@Environment(EnvType.CLIENT)
fun warnPlayer( s: String, actionBar: Boolean ) {

    val player = player();   if ( player == null ) { modPrint(s); return }

    warnPlayer( player, s, actionBar )

}

/** Warns player, not using the action bar. */
fun warnPlayer( player: PlayerEntity, s: String ) { warnPlayer( player, s, false ) }

fun warnPlayer( player: PlayerEntity, s: String, actionBar: Boolean ) {

    var s = s;      if ( !actionBar ) s = chatTitle + s

    player.sendMessage( Text.of(s), actionBar )

}