package com.enginemachiner.honkytones.items.console

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackHand
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class DigitalConsole : Item(settings), StackScreen {

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);        val canOpen = canOpenScreen( user, stack )

        val action = TypedActionResult.pass(stack);     if ( world.isClient || !canOpen ) return action

        user.openHandledScreen( createMenu(stack) )

        return action

    }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();    nbt.putInt( "Octave", 4 );      return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) { trackHand(stack) }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick(stack, world, entity, slot, selected)

        checkDamage(stack, world, entity)

    }

    fun createMenu( stack: ItemStack ): NamedScreenHandlerFactory {

        val factory = DigitalConsoleScreenHandler.factory(stack)
        val text = Text.of("Digital Console Screen")

        return SimpleNamedScreenHandlerFactory( factory, text )

    }

    private fun checkDamage( stack: ItemStack, world: World, entity: Entity ) {

        val nbt = nbt(stack);       val damage = world.isClient || !nbt.contains("damageStack")


        if (damage) return;             entity as PlayerEntity


        damage(stack);      nbt.remove("damageStack")

    }

    private companion object {

        val settings: Settings = modItemSettings().maxDamage(6)

    }

}