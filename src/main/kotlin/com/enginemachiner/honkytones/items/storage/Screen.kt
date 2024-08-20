package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.harmony.*
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.screen.ScreenHandlerFactory
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType

class StorageScreenHandler(

    syncID: Int,        private val playerInventory: PlayerInventory,       private val inventory: Inventory

) : HarmonyScreenHandler( type, syncID ) {


    constructor( syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, inventory() )

    constructor( stack: ItemStack, syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, inventory(stack) )


    private val player = playerInventory.player;        private val stackSlot: Int?

    private val stack = handItem( player, MusicalStorage::class )

    private val storage = stack.item as MusicalStorage


    init {

        storage.open(stack)


        checkSize( inventory, inventory.size() );       inventory.onOpen(player)


        slots( 2, 8, 17f, 25f, inventory ).forEach { addSlot(it) }

        playerSlots( 8f, 76f, playerInventory ).forEach { addSlot(it) }


        stackSlot = slotIndex(slots, stack)

    }

    override fun close( player: PlayerEntity ) {

        super.close(player);        storage.close(stack)

    }

    override fun canUse( player: PlayerEntity ): Boolean { return true }

    private fun insertAllowed( slotIndex: Int ): Boolean {

        val stack = stacks[slotIndex]

        if ( !isModItem(stack) ) return false

        return insertItem( slotIndex, 0, inventory.size() )

    }

    private fun insertAny( slotIndex: Int ): Boolean {

        val start = inventory.size()

        val limit = start + playerInventory.size() * 0.5f

        return insertItem( slotIndex, start, limit.toInt() )

    }

    override fun quickMove( player: PlayerEntity, slotIndex: Int ): ItemStack {

        val slot = slots[slotIndex];        val stack = slot.stack;         val isEmpty = stack.isEmpty

        val empty = ItemStack.EMPTY


        if ( isEmpty ) return empty


        val success = insertAllowed(slotIndex) || insertAny(slotIndex)

        if ( !success ) return empty


        slot.markDirty();       return stack.copy()

    }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val onSlots = slotIndex < inventory.size()


        fun click() { super.onSlotClick(slotIndex, button, actionType, player) }

        fun canPickUp(): Boolean {

            return canPickUp( onSlots, ::isModItem ) || !onSlots

        }

        fun onSwap(): Boolean {

            if ( actionType != SlotActionType.SWAP ) return true


            val isStorage = slotIndex(button) == stackSlot

            val canSwap = canSwap( button, slotIndex, ::isModItem ) && onSlots

            return !isStorage && ( canSwap || !onSlots )

        }

        // slotIndex < 0 are used for networking internals.

        if ( slotIndex < 0 ) { click(); return }


        val isStorage = slotIndex == stackSlot

        if ( isStorage || !canPickUp() || !onSwap() ) return

        click()

    }

    companion object : ModID {

        object Translations {

            val name = Translation.item("musical_storage")

        }

        private const val INVENTORY_SIZE = MusicalStorageInventory.INVENTORY_SIZE

        private fun inventory( stack: ItemStack? = null ): Inventory {

            stack ?: return SimpleInventory(INVENTORY_SIZE)

            return MusicalStorageInventory(stack)

        }

        fun factory( stack: ItemStack ): ScreenHandlerFactory {

            return ScreenHandlerFactory { id, inventory, _ ->

                StorageScreenHandler( stack, id, inventory )

            }

        }

        val type = ScreenHandlerType(::StorageScreenHandler)

        fun register() { Registry.register( Registries.SCREEN_HANDLER, classID(), type ) }

    }

}