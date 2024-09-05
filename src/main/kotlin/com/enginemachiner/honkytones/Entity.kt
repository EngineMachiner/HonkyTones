package com.enginemachiner.honkytones

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.Sender
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import kotlin.reflect.KClass

/** Can mute entities. */
interface Silencer {

    fun mute( player: PlayerEntity, entity: Entity, kClass: KClass<out Entity> ): Boolean {

        val isSneaking = player.isInSneakingPose

        val isInstance = kClass.isInstance(entity);         val world = player.world


        val mute = !world.isClient && isSneaking && isInstance

        if ( !mute ) return false


        val sender = Sender(id) { it.write( entity.id ) }

        sender.toClient(player);        return true

    }

    private companion object : ModID { val id = netID("mute") }

}