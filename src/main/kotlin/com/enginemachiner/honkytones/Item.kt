package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AirBlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.item.ToolItem
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Hand
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import kotlin.reflect.KClass

val hands = arrayOf( Hand.MAIN_HAND, Hand.OFF_HAND )

private val equipment = arrayOf( EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND )

fun breakEquipment( entity: LivingEntity?, stack: ItemStack ) {

    val index = NBT.get(stack).getInt("Hand")

    entity!!.sendEquipmentBreakStatus( equipment[index] )

}

fun modItem( kclass: KClass<*> ): Item {

    val className = ModID.className(kclass);    return modItem(className)

}

fun modItem(s: String): Item { return Registry.ITEM.get( modID(s) ) }

fun isModItem(stack: ItemStack): Boolean { return Registry.ITEM.getId( stack.item ).namespace == MOD_NAME }

// fun isModItem(item: Item): Boolean { return Registry.ITEM.getId(item).namespace == MOD_NAME }

// Create item icon for the itemgroup.
var itemGroup: ItemGroup? = null

object ItemGroup: Item( Settings() ), ModID {

    private val id = modID("item_group")

    init {

        itemGroup = FabricItemGroupBuilder.create(id)!!
            .icon { defaultStack }.build()

    }

}

fun defaultSettings(): Item.Settings {

    return Item.Settings().group(itemGroup).maxCount(1)

}

interface StackMenu {

    fun canOpenMenu( user: PlayerEntity, stack: ItemStack ): Boolean {

        val stack2 = user.handItems.find { it != stack }!!

        return stack2.item is AirBlockItem

    }

}

private interface Trackable {

    fun tick( stack: ItemStack?, world: World?, entity: Entity?, slot: Int ) {

        stack!!.holder = entity

        if ( world!!.isClient ) return;     trackTick( stack, slot )

        if ( !NBT.has(stack) ) setupNBT(stack);     val nbt = NBT.get(stack)

        if ( nbt.contains("BlockPos") ) nbt.remove("BlockPos")

    }

    fun trackTick( stack: ItemStack, slot: Int ) {}

    fun getSetupNBT(stack: ItemStack): NbtCompound

    fun setupNBT(stack: ItemStack) {

        val nbt = stack.nbt!!;      nbt.put( MOD_NAME, getSetupNBT(stack) )

    }

}

abstract class ToolItem( material: ToolMaterial, settings: Settings ) : ToolItem( material, settings ), Trackable, ModID {

    override fun allowNbtUpdateAnimation(
        player: PlayerEntity?, hand: Hand?, oldStack: ItemStack?, newStack: ItemStack?
    ): Boolean { return false }

    override fun inventoryTick(
        stack: ItemStack?, world: World?, entity: Entity?, slot: Int, selected: Boolean
    ) { tick( stack, world, entity, slot ) }

}

abstract class Item(settings: Settings) : Item(settings), Trackable, ModID {

    override fun allowNbtUpdateAnimation(
        player: PlayerEntity?, hand: Hand?, oldStack: ItemStack?, newStack: ItemStack?
    ): Boolean { return false }

    override fun inventoryTick(
        stack: ItemStack?, world: World?, entity: Entity?, slot: Int, selected: Boolean
    ) { tick( stack, world, entity, slot ) }

}