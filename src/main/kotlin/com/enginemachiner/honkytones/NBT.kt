package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object NBT {

    @JvmStatic
    fun has(stack: ItemStack): Boolean { return stack.orCreateNbt.contains(MOD_NAME) }

    @JvmStatic
    fun get(stack: ItemStack): NbtCompound { return stack.nbt!!.getCompound(MOD_NAME)!! }

    fun id(stack: ItemStack): Int { return get(stack).getInt("ID") }

    /** Stores the display temporally for it to be updated later. */
    fun keepDisplay( stack: ItemStack, next: NbtCompound ) {

        if ( next.contains("resetDisplay") ) return;    val former = stack.nbt!!

        next.put( "display", former.getCompound("display") )

    }

    private fun putInt( nbt: NbtCompound, key: String, value: Int ) {

        val b = !nbt.contains(key) || nbt.getInt(key) != value
        if (b) nbt.putInt( key, value )

    }

    fun trackPlayer(stack: ItemStack) {

        val holder = stack.holder;      if ( holder !is PlayerEntity ) return

        putInt( get(stack), "PlayerID", holder.id ) // TODO: Consider using UUID as string.

    }

    fun trackHand(stack: ItemStack) {

        val holder = stack.holder;      if ( holder !is PlayerEntity ) return

        val index = holder.itemsHand.indexOf(stack)

        putInt( get(stack), "Hand", index )

    }

    fun trackSlot( stack: ItemStack, slot: Int ) { putInt( get(stack), "Slot", slot ) }

    private fun getBlockStack( nbt: NbtCompound, world: World ): ItemStack? {

        if ( !nbt.contains("BlockPos") ) return null

        val slot = nbt.getInt("Slot")
        val list = nbt.getString("BlockPos").replace( " ", "" ).split(',')
        val blockPos = BlockPos( list[0].toDouble(), list[1].toDouble(), list[2].toDouble() )
        val inventory = world.getBlockEntity(blockPos) ?: return null

        inventory as Inventory

        return inventory.getStack(slot)

    }

    private fun getStack( player: PlayerEntity, nbt: NbtCompound ): ItemStack? {

        // Get block entity inventory stack.

        val stack = getBlockStack( nbt, player.world );     if ( stack != null ) return stack

        val inventory = player.inventory

        if ( nbt.contains("Hand") ) {

            val hand = nbt.getInt("Hand");      return player.getStackInHand( hands[hand] )

        } else if ( nbt.contains("Slot") ) {

            val slot = nbt.getInt("Slot");      return inventory.getStack(slot)

        } else if ( nbt.contains("ID") ) {

            return inventoryAsList(inventory).filter { has(it) }
                .find { id(it) == nbt.getInt("ID") }

        }

        return null

    }

    private val networkID = modID("network_nbt")

    @JvmStatic
    @Environment(EnvType.CLIENT)
    fun networkNBT(nbt: NbtCompound) {

        if ( !canNetwork() ) return

        val buf = PacketByteBufs.create();      buf.writeNbt(nbt)

        ClientPlayNetworking.send( networkID, buf )

    }

    fun networking() {

        ServerPlayNetworking.registerGlobalReceiver(networkID) {

            server: MinecraftServer, player: ServerPlayerEntity,
            _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

            val nbt = buf.readNbt()!!

            server.send ( ServerTask( server.ticks ) {

                val stack = getStack( player, nbt ) ?: return@ServerTask

                val currentNbt = stack.nbt!!

                // Display name.
                if ( nbt.contains("display") ) {

                    currentNbt.put( "display", nbt.getCompound("display") )

                    nbt.remove("display")

                } else if ( nbt.contains("resetDisplay") ) {

                    stack.removeCustomName();   nbt.remove("resetDisplay")

                }

                currentNbt.put( MOD_NAME, nbt )

            } )

        }

    }

}
