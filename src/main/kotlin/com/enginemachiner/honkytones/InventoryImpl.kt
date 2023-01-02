package com.enginemachiner.honkytones

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.SidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.Direction

@FunctionalInterface
interface ImplementedInventory : SidedInventory {

    fun getItems(): DefaultedList<ItemStack>

    override fun getAvailableSlots(side: Direction?): IntArray {

        val result = IntArray( getItems().size )
        for (i in result.indices) result[i] = i

        return result

    }

    override fun canInsert(slot: Int, stack: ItemStack?, dir: Direction?): Boolean { return true }
    override fun canExtract(slot: Int, stack: ItemStack?, dir: Direction?): Boolean { return true }

    override fun size(): Int { return getItems().size }

    override fun isEmpty(): Boolean {

        for (i in 0 until size()) {
            val stack = getStack(i)
            if (!stack.isEmpty) return false
        }

        return true

    }

    override fun getStack(slot: Int): ItemStack { return getItems()[slot] }

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        val result = Inventories.splitStack(getItems(), slot, amount)
        if (!result.isEmpty) markDirty()

        return result
    }

    override fun removeStack(slot: Int): ItemStack {
        return Inventories.removeStack(getItems(), slot)
    }

    override fun setStack(slot: Int, stack: ItemStack?) {
        getItems()[slot] = stack!!

        if (stack.count > maxCountPerStack) stack.count = maxCountPerStack
    }

    override fun clear() { getItems().clear() }

    override fun markDirty() {}

    override fun canPlayerUse(player: PlayerEntity?): Boolean { return true }

    companion object {

        fun of( items: DefaultedList<ItemStack>):
                    () -> DefaultedList<ItemStack> { return { items } }

        /*

        fun ofSize(size: Int): () -> DefaultedList<ItemStack> {
            return of( DefaultedList.ofSize(size, ItemStack.EMPTY) )
        }

        */

    }

}

open class CustomInventory( stack: ItemStack, size: Int ) : ImplementedInventory {

    private val stack: ItemStack
    private val items = DefaultedList.ofSize( size, ItemStack.EMPTY )

    init {
        this.stack = stack
        val nbt = stack.getSubNbt("Items")
        if (nbt != null) Inventories.readNbt(nbt, items)
    }

    override fun getItems(): DefaultedList<ItemStack> { return items }

    override fun markDirty() {
        val tag = stack.getOrCreateSubNbt("Items")
        Inventories.writeNbt(tag, items)
    }

}