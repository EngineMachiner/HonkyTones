package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.MusicalStorageInventory
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList

@Environment(EnvType.CLIENT)
class InstrumentReceiver( private val deviceId: String ) : GenericReceiver() {

    private fun checkInventory( inv: DefaultedList<ItemStack>, list: MutableList<ItemStack> ) {

        for ( stack in inv ) {

            val item = stack.item
            if ( ITEM_GROUP.contains(stack) && !list.contains(stack) ) {

                val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)
                val hasTag = nbt.getString("MIDI Device") == deviceId

                if ( hasTag ) list.add(stack)

                if ( item is MusicalStorage ) {
                    val inventory = MusicalStorageInventory(stack).getItems()
                    checkInventory( inventory, list )
                }

            }

        }

    }

    /** Get instrument stacks */
    private fun getStacks( player: ClientPlayerEntity ): MutableList<ItemStack> {

        val list = mutableListOf<ItemStack>()
        checkInventory( player.inventory.main, list )
        checkInventory( player.inventory.offHand, list )

        return list

    }

    override fun close() { println( Base.DEBUG_NAME + "$deviceId device has been closed." ) }

    override fun init() {
        val client = MinecraftClient.getInstance()
        entity = client.player;         instruments = getStacks(client.player!!)
    }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {
        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        return channel + 1 == nbt.getInt("MIDI Channel")
    }

    override fun onPlay(sound: CustomSoundInstance, stack: ItemStack, entity: Entity) {
        spawnPlayerParticleChance( entity as PlayerEntity )
        super.onPlay(sound, stack, entity)
    }

    companion object {

        fun spawnPlayerParticleChance( player: PlayerEntity ) {

            val key = "playerParticles"
            val b1 = clientConfig[key] as Boolean
            if ( (0..4).random() == 0 ) {

                if (b1) Instrument.spawnNoteParticle(player, "device")

                val buf = PacketByteBufs.create();      buf.writeInt(player.id)
                buf.writeString(key).writeString("device")

                val id = Identifier( Base.MOD_NAME, "entity_spawn_particle" )
                ClientPlayNetworking.send(id, buf)

            }

        }

    }

}
