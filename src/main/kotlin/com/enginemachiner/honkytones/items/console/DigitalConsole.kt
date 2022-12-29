package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class DigitalConsole : Item( createDefaultItemSettings().maxDamage(6) ) {

    override fun use(world: World?, user: PlayerEntity?, hand: Hand?):
            TypedActionResult<ItemStack> {

        val stack = user!!.getStackInHand(hand)
        val action = TypedActionResult.pass(stack)

        if (world!!.isClient) return action

        user.openHandledScreen( createMenu(stack) )

        return action

    }

    override fun inventoryTick( stack: ItemStack?, world: World?, entity: Entity?,
                                slot: Int, selected: Boolean ) {

        if ( !world!!.isClient ) {

            var nbt = stack!!.orCreateNbt

            if ( !nbt.contains( Base.MOD_NAME ) ) loadNbtData(nbt)

            nbt = nbt.getCompound( Base.MOD_NAME )

            trackHandOnNbt( stack, entity!! )

            if ( nbt.contains("shouldDamage") && entity is PlayerEntity ) {
                stack.damage( 1, entity ) { sendStatus(entity, stack) }
                nbt.remove("shouldDamage")
            }

        }

    }

    private fun loadNbtData( nbt: NbtCompound ) {

        val innerNbt = NbtCompound()
        innerNbt.putInt("Octave", 4)

        nbt.put( Base.MOD_NAME, innerNbt )

    }

    fun createMenu(stack: ItemStack ): NamedScreenHandlerFactory {

        val title = Translation.get("item.honkytones.digitalconsole")
        return SimpleNamedScreenHandlerFactory( {
                syncID: Int, inv: PlayerInventory, _: PlayerEntity ->
            DigitalConsoleScreenHandler( stack, syncID, inv )
        }, Text.of("§f$title") )

    }

}