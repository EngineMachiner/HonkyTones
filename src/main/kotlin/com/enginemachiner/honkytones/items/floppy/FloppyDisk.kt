package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.NBT.keepDisplay
import com.enginemachiner.honkytones.NBT.trackHand
import com.enginemachiner.honkytones.NBT.trackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class FloppyDisk : Item( defaultSettings().maxDamage( damageSeed() ) ), StackMenu {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound()

        nbt.putString( "Path", "" );      nbt.putInt( "ID", stack.hashCode() )

        // Midi file data.
        nbt.putFloat( "Rate", 1f );       nbt.putInt( "timesWritten", 0 )

        // Audio stream data.
        nbt.putFloat( "Volume", 1f )

        return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) {

        trackHand( stack, true );       trackPlayer(stack)

        trackDamage(stack)

    }

    override fun inventoryTick( stack: ItemStack?, world: World?, entity: Entity?, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        if ( !world!!.isClient ) return;            queryTick(stack!!)

    }

    override fun use( world: World?, user: PlayerEntity?, hand: Hand? ): TypedActionResult<ItemStack> {

        val stack = user!!.getStackInHand(hand);            val canOpen = canOpenMenu( user, stack )

        val action = super.use( world, user, hand );        if ( !world!!.isClient || !canOpen ) return action

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

        fun networking() {

            val id = netID("focus")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, player: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readInt();         val handIndex = buf.readInt()

                server.send( ServerTask( server.ticks ) {

                    val hand = hands[handIndex];        val handStack = player.getStackInHand(hand)

                    val inventory = player.inventory;   var floppy: ItemStack? = null

                    var slot = 0;       val range = 0 until inventory.size()

                    range.forEach {

                        if ( floppy != null ) return@forEach

                        val stack = inventory.getStack(it);     slot = it

                        if ( !NBT.has(stack) ) return@forEach

                        val nbt = NBT.get(stack)
                        if ( nbt.getInt("ID") != id ) return@forEach

                        slot = it;      floppy = stack

                    }

                    floppy ?: return@ServerTask

                    inventory.setStack( slot, handStack )

                    player.setStackInHand( hand, floppy )

                } )

            }

        }

    }

    private fun trackDamage(stack: ItemStack) {

        val nbt = NBT.get(stack);                   val holder = stack.holder

        val times = nbt.getInt("timesWritten")

        if ( times <= maxDamage ) return;     holder as PlayerEntity

        stack.damage( maxDamage, holder ) { breakEquipment( it, stack ) }

    }

    /** Queries the source title when requested. */
    private fun queryTick(stack: ItemStack) {

        val nbt = NBT.get(stack);       val holder = stack.holder

        var noAction = true
        for ( name in actions ) noAction = noAction && !nbt.contains(name)

        if (noAction) return

        for ( name in actions ) if ( nbt.contains(name) ) nbt.remove(name)

        coroutine.launch {

            Thread.currentThread().name = "HonkyTones Floppy thread"

            val path = nbt.getString("Path")
            val info = infoRequest(path) ?: return@launch

            holder as PlayerEntity

            if ( !holder.inventory.contains(stack) ) return@launch

            stack.setCustomName( Text.of( info.title ) )

            keepDisplay( stack, nbt );      focusStack(nbt)

            closeFloppyScreen();            nbt.remove("onQuery")

            networkNBT(nbt)

        }

    }

    private fun focusStack( nbt: NbtCompound ) {

        val id = netID("focus")

        val buf = PacketByteBufs.create()

        buf.writeInt( nbt.getInt("ID") )
        buf.writeInt( nbt.getInt("Hand") )

        ClientPlayNetworking.send( id, buf )

    }

    private fun closeFloppyScreen() {

        if ( currentScreen() !is FloppyDiskScreen ) return

        val id = modID("close_screen")

        ClientPlayNetworking.send( id, PacketByteBufs.empty() )

    }

}