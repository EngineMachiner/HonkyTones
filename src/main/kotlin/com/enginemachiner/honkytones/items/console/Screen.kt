package com.enginemachiner.honkytones.items.console

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler.Companion.console
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.screen.ScreenHandlerFactory
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text

/*
    There is a lot of manual tweaking around here with slots and the stack inventory.
    It is what it is.
*/

class DigitalConsoleScreenHandler(

    syncID: Int, private val playerInventory: PlayerInventory,      inventory: Inventory

) : HarmonyScreenHandler( type, syncID ) {


    constructor( syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, inventory() )

    constructor( stack: ItemStack, syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, inventory(stack) )


    private val player = playerInventory.player;        private val world = player.world


    init {

        checkSize( inventory, inventory.size() )

        inventory.onOpen(player)


        val slot = Slot( inventory, 0, 220, 160 )

        addSlot(slot)


        check()

    }

    private fun instrument(): ItemStack { return inventory( console(player) ).getStack(0) }

    private fun check() {

        val instrument = instrument();            if ( instrument.isEmpty ) return


        val main = playerInventory.main

        val has = main.any { NBT.equals( it, instrument ) }

        if (has) return


        val slot = getSlot(0);          slot.stack = ItemStack.EMPTY

        slot.markDirty()


        if ( player.world.isClient ) PickStackScreenHandler.set( player, slot.stack )

    }

    override fun close( player: PlayerEntity ) {

        super.close(player);        val stack = instrument()

        if ( stack.isEmpty || !world.isClient ) return

        InstrumentItem.stopSounds(stack)

    }

    override fun canUse( player: PlayerEntity ): Boolean { return true }

    override fun quickMove( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val factory = PickStackScreenHandler.factory
        val text = Text.of("Pick Stack Screen")

        val screenFactory = SimpleNamedScreenHandlerFactory( factory, text )

        player.openHandledScreen(screenFactory)

    }

    companion object : ModID {

        fun console( player: PlayerEntity ): ItemStack {

            return handItem( player, DigitalConsole::class )

        }

        fun inventory( stack: ItemStack? = null ): Inventory {

            val size = 1;           stack ?: return SimpleInventory(size)

            return StackInventory( stack, size )

        }

        fun factory( stack: ItemStack ): ScreenHandlerFactory {

            return ScreenHandlerFactory { id, inventory, _ ->

                DigitalConsoleScreenHandler( stack, id, inventory )

            }

        }

        val type = ScreenHandlerType( ::DigitalConsoleScreenHandler )

        fun register() {

            Registry.register( Registries.SCREEN_HANDLER, classID(), type )

        }

    }

}

class PickStackScreenHandler( syncID: Int, playerInventory: PlayerInventory ) : HarmonyScreenHandler( type, syncID ) {

    private val player = playerInventory.player

    private val stack = console(player)

    private val console: DigitalConsole = stack.item as DigitalConsole


    init {

        playerSlots( 8f, 46f, playerInventory ).forEach { addSlot(it) }

    }

    private fun goBack() {

        val factory = console.createMenu(stack)

        player.openHandledScreen(factory)

    }

    override fun canUse(player: PlayerEntity): Boolean { return true }

    override fun quickMove( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        if ( slotIndex < 0 || player.world.isClient ) return


        val selected = slots[slotIndex].stack

        val item = selected.item;           if ( item !is InstrumentItem ) return


        set( player, selected )


        val id = DigitalConsoleScreenHandler.netID("sync")

        val slot = player.inventory.getSlotWithStack(selected)

        val sender = Sender(id) { it.write(slot) };      sender.toClient(player)


        goBack()

    }

    companion object : ModID {

        fun set( player: PlayerEntity, selected: ItemStack ) {

            val inventory = StackInventory( console(player), 1 )

            inventory.setStack( 0, selected );      inventory.markDirty()

        }

        val factory = ScreenHandlerFactory { id, inventory, _ ->

            PickStackScreenHandler(id, inventory)

        }

        val type = ScreenHandlerType(::PickStackScreenHandler)

        fun register() {

            Registry.register( Registries.SCREEN_HANDLER, classID(), type )

        }

    }

}
