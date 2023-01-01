package com.enginemachiner.honkytones

import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AirBlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import net.minecraft.world.World

val hands = arrayOf( Hand.MAIN_HAND, Hand.OFF_HAND )
val equipment = arrayOf( EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND )

fun sendStatus(entity: LivingEntity?, stack: ItemStack) {
    val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
    val index = nbt.getInt("hand")
    entity!!.sendEquipmentBreakStatus( equipment[index] )
}

fun createStackIfMissing( stacks: MutableList<ItemStack>, name: String, index: Int ) {

    val stack = stacks.elementAtOrNull(index)

    if ( stack == null || stack.item is AirBlockItem) {
        while ( stacks.size < index ) stacks.add( ItemStack( Items.AIR ) )
        val id = Identifier( Base.MOD_NAME, name )
        val item = Registry.ITEM.get( id )
        stacks.add( index, ItemStack( item ) )
    }

}

fun getRegisteredItem( s: String ): Item {
    return Registry.ITEM.get( Identifier( Base.MOD_NAME, s ) )
}

fun isModItem( item: Item): Boolean {
    return Registry.ITEM.getId(item).namespace == Base.MOD_NAME
}

fun trackPlayerOnNbt(nbt: NbtCompound, entity: Entity, world: World) {

    val tempPlayer = world.players.find { it.uuidAsString == nbt.getString("PlayerUUID") }
    if ( tempPlayer != entity ) nbt.putString( "PlayerUUID", entity.uuidAsString )

}

fun trackHandOnNbt( stack: ItemStack, entity: Entity ) {

    val nbt = stack.nbt!!.getCompound( Base.MOD_NAME )

    val index = entity.itemsHand.indexOf(stack)
    val b = !nbt.contains("hand") || nbt.getInt("hand") != index
    if ( entity is PlayerEntity && index >= 0 && b ) nbt.putInt( "hand", index )

}

fun writeDisplayOnNbt(stack: ItemStack, toNbt: NbtCompound) {
    val formerNbt = stack.nbt!!
    if ( !toNbt.contains("removeName") ) {
        val displayNbt = formerNbt.getCompound("display")
        toNbt.put("display",  displayNbt)
    }
}