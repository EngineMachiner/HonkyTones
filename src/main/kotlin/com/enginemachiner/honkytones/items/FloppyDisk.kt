package com.enginemachiner.honkytones.items

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackPlayer
import com.enginemachiner.harmony.NBT.trackSlot
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class FloppyDisk : Item( settings() ), StackScreen {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound()

        nbt.put( "Settings", NbtCompound() );       nbt.putInt( "timesWritten", 0 )

        nbt.putString( "Path", "" );        nbt.putInt( "ID", stack.hashCode() )

        return setColor(nbt)

    }

    override fun trackTick( stack: ItemStack, slot: Int ) {

        trackPlayer( stack, "Host" );     trackDamage(stack)

        trackSlot( stack, slot )

    }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        query( world, stack, entity )

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);              val canOpen = canOpenScreen( user, stack )

        val action = super.use( world, user, hand );        if ( !canOpen ) return action

        setScreen(user, hand);       return action

    }

    companion object : ModID, ColorItem {

        val registeredItem = FloppyDisk()

        fun isState( stack: ItemStack, state: String ): Boolean {

            return state == nbt(stack).getString("nameState")

        }

        fun setState( stack: ItemStack, state: String ) {

            nbt(stack).putString( "nameState", state )

        }

        fun interrupt( stack: ItemStack ) {

            if ( !nbt(stack).contains("nameState") ) return

            setState( stack, "Interrupted" )

        }

        /** Get item settings. */
        private fun settings(): Settings {

            val damageSeed = ( 2..3 ).random()

            return modItemSettings().maxDamage(damageSeed)

        }

        /** Get player settings. */
        fun settings( stack: ItemStack, player: PlayerEntity ): NbtCompound {

            val uuid = player.uuidAsString

            val settings = nbt(stack).get("Settings") as NbtCompound

            if ( settings.contains(uuid) ) return settings.get(uuid) as NbtCompound

            val new = NbtCompound();    settings.put( uuid, new )

            new.putDouble( "Volume", 1.0 );     return new

        }

        private fun setScreen( user: PlayerEntity, hand: Hand ) {

            if ( user.world.isClient ) return

            val id = netID("set_screen");       val i = hand.ordinal

            val sender = Sender(id) { it.write(i) };        sender.toClient(user)

        }

        /** Tries to set the source title doing a query. */
        private fun query( world: World, stack: ItemStack, entity: Entity ) {

            val nbt = nbt(stack)

            if ( world.isClient || entity !is PlayerEntity || !nbt.contains("nameState") ) return


            val id = netID("query");        val slot = nbt.getInt("Slot")

            val sender = Sender(id) { it.write(slot) };         sender.toClient(entity)

        }

    }

    private fun trackDamage(stack: ItemStack) {

        val nbt = nbt(stack);           val times = nbt.getInt("timesWritten")

        if ( times <= maxDamage ) return;       damage( stack, maxDamage )

    }

}