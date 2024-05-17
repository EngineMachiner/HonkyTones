package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.saveDisplay
import com.enginemachiner.harmony.NBT.sendNBT
import com.enginemachiner.harmony.NBT.trackPlayer
import com.enginemachiner.honkytones.YTDLP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

// TODO: I want different color floppies.
class FloppyDisk : Item( modItemSettings().maxDamage( damageSeed() ) ), StackScreen {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound()

        nbt.put( "Settings", NbtCompound() ) // Clients settings.

        nbt.putString( "Path", "" );        nbt.putInt( "ID", stack.hashCode() )

        nbt.putInt( "timesWritten", 0 )

        return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) {

        trackPlayer( stack, "Host" );     trackDamage(stack)

    }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        if ( !world.isClient ) return;            titleQuery(stack)

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);              val canOpen = canOpenScreen( user, stack )

        val action = super.use( world, user, hand );        if ( !world.isClient || !canOpen ) return action

        client().setScreen( FloppyDiskScreen(stack) );      return action

    }

    companion object : ModID {

        private val actions = listOf( "Interrupted", "onFetch" )

        private val coroutine = CoroutineScope( Dispatchers.IO )

        private fun damageSeed(): Int { return ( 2..3 ).random() }

        fun settings( stack: ItemStack, player: PlayerEntity ): NbtCompound {

            val uuid = player.uuidAsString

            val settings = NBT.get(stack).get("Settings") as NbtCompound

            if ( settings.contains(uuid) ) return settings.get(uuid) as NbtCompound

            val new = NbtCompound();    settings.put( uuid, new )

            new.putDouble( "Volume", 1.0 );     return new

        }

        fun interrupt(stack: ItemStack) {

            val nbt = NBT.get(stack);       if ( !nbt.contains("onFetch") ) return

            nbt.putBoolean( "Interrupted", true )

        }

    }

    private fun trackDamage(stack: ItemStack) {

        val nbt = NBT.get(stack);                   val holder = stack.holder

        val times = nbt.getInt("timesWritten")

        if ( times <= maxDamage ) return;     holder as PlayerEntity

        stack.damage( maxDamage, holder ) { breakEquipment( it, stack ) }

    }

    /* Why does this function exec twice? */
    /** Queries the source title when requested. */
    private fun titleQuery(stack: ItemStack) {

        if ( !YTDLP.exists() ) return


        val holder = stack.holder as PlayerEntity

        val nbt = NBT.get(stack);       var noAction = true


        for ( name in actions ) noAction = noAction && !nbt.contains(name)

        if (noAction) return

        for ( name in actions ) if ( nbt.contains(name) ) nbt.remove(name)

        coroutine.launch {

            Thread.currentThread().name = "HonkyTones Floppy thread"

            val path = nbt.getString("Path")

            val info = YTDLP(path).info ?: return@launch

            val list = inventoryList( holder.inventory )


            val stack2 = list.find { NBT.has(it) && NBT.id(it) == NBT.id(stack) }

            if ( stack2 == null ) return@launch


            val nbt2 = NBT.get(stack2);     val path2 = nbt2.getString("Path")

            if ( path != path2 ) return@launch


            stack.setCustomName( Text.of( info.title ) )

            saveDisplay( stack, nbt );      closeScreen()

            nbt.putBoolean( "displaySet", true );    sendNBT(nbt)

        }

    }

    private fun closeScreen() {

        val screen = currentScreen() ?: return

        if ( screen.shouldPause() ) return


        val id = modID("close_screen");     Sender(id).toServer()

    }

}