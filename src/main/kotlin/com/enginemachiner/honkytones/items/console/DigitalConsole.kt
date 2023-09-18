package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.NBT.trackHand
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class DigitalConsole : Item( defaultSettings().maxDamage(6) ), StackMenu {

    override fun use( world: World?, user: PlayerEntity?, hand: Hand? ): TypedActionResult<ItemStack> {

        val stack = user!!.getStackInHand(hand);        val canOpen = canOpenMenu( user, stack )

        val action = TypedActionResult.pass(stack);     if ( world!!.isClient || !canOpen ) return action

        user.openHandledScreen( createMenu(stack) )

        return action

    }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();    nbt.putInt( "Octave", 4 );      return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) { trackHand(stack) }

    override fun inventoryTick(
        stack: ItemStack?, world: World?, entity: Entity?, slot: Int, selected: Boolean
    ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        val nbt = NBT.get(stack!!)

        if ( world!!.isClient || !nbt.contains("damage") ) return

        entity as PlayerEntity;         nbt.remove("damage")

        stack.damage( 1, entity ) { breakEquipment(entity, stack) }

    }

    fun createMenu(stack: ItemStack): NamedScreenHandlerFactory {

        val title = Translation.item("digital_console")

        return SimpleNamedScreenHandlerFactory(

            {
                syncID: Int, playerInventory: PlayerInventory, _: PlayerEntity ->

                DigitalConsoleScreenHandler( stack, syncID, playerInventory )
            },

            Text.of("§f$title")

        )

    }

}