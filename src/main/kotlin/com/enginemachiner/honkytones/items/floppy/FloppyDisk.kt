package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.NBT.keepDisplay
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.NBT.trackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import java.io.File

// TODO: I want different color floppies.
class FloppyDisk : Item( defaultSettings().maxDamage( damageSeed() ) ), StackMenu {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound()

        nbt.putString( "Path", "" );      nbt.putInt( "ID", stack.hashCode() )

        // Midi file data.
        nbt.putFloat( "Rate", 1f );       nbt.putInt( "timesWritten", 0 )

        // Audio stream data.
        nbt.putFloat( "Volume", 1f );     return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) { trackPlayer(stack);     trackDamage(stack) }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        if ( !world.isClient ) return;            queryTick(stack)

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);            val canOpen = canOpenMenu( user, stack )

        val action = super.use( world, user, hand );        if ( !world.isClient || !canOpen ) return action

        client().setScreen( FloppyDiskScreen(stack) );      return action

    }

    companion object : ModID {

        private val actions = listOf( "interrupted", "onQuery" )

        private val coroutine = CoroutineScope( Dispatchers.IO )

        private fun damageSeed(): Int { return ( 2..3 ).random() }

        fun interrupt(stack: ItemStack) {

            val nbt = NBT.get(stack);       if ( !nbt.contains("onQuery") ) return

            nbt.putBoolean( "interrupted", true )

        }

        fun missingMessage(name: String): String {

            val missingMessage = Translation.get("error.midi_file_missing")

            return "$name $missingMessage"

        }

    }

    private fun trackDamage(stack: ItemStack) {

        val nbt = NBT.get(stack);                   val holder = stack.holder

        val times = nbt.getInt("timesWritten")

        if ( times <= maxDamage ) return;     holder as PlayerEntity

        stack.damage( maxDamage, holder ) { breakEquipment( it, stack ) }

    }

    /* TODO: This interrupt query thing keeps requesting twice.
    *  To replicate set a link, interrupt and spam open a menu. */
    /** Queries the source title when requested. */
    private fun queryTick(stack: ItemStack) {

        if ( !File( ytdlPath() ).exists() ) return

        val holder = stack.holder as PlayerEntity

        val nbt = NBT.get(stack);       var noAction = true

        for ( name in actions ) noAction = noAction && !nbt.contains(name)

        if (noAction) return

        for ( name in actions ) if ( nbt.contains(name) ) nbt.remove(name)

        coroutine.launch {

            Thread.currentThread().name = "HonkyTones Floppy thread"

            val path = nbt.getString("Path")
            val info = infoRequest(path) ?: return@launch

            val setTitle = stack.isEmpty || !holder.inventory.contains(stack)
                    || nbt.contains("hasRequestDisplay")

            if (setTitle) return@launch

            stack.setCustomName( Text.of( info.title ) )

            keepDisplay( stack, nbt );  closeScreen()

            nbt.putBoolean( "hasRequestDisplay", true );    networkNBT(nbt)

        }

    }

    private fun closeScreen() {

        val screen = currentScreen()

        if ( screen == null || screen.shouldPause() ) return

        val id = modID("close_screen")

        ClientPlayNetworking.send( id, PacketByteBufs.empty() )

    }

}