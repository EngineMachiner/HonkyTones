package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import com.enginemachiner.honkytones.items.instruments.*
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.registry.FuelRegistry
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
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.world.World
import kotlin.reflect.KClass

const val HANDS_ANGLE = 15

val hands = arrayOf( Hand.MAIN_HAND, Hand.OFF_HAND )

private val equipment = arrayOf( EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND )

fun breakEquipment( entity: LivingEntity, stack: ItemStack ) {

    val index = NBT.get(stack).getInt("Hand")

    entity.sendEquipmentBreakStatus( equipment[index] )

}

fun modItem( kclass: KClass<*> ): Item {

    val className = ModID.className(kclass);    return modItem(className)

}

fun modItem(s: String): Item { return Registries.ITEM.get( modID(s) ) }

fun isModItem(stack: ItemStack): Boolean { return Registries.ITEM.getId( stack.item ).namespace == MOD_NAME }

// fun isModItem(item: Item): Boolean { return Registry.ITEM.getId(item).namespace == MOD_NAME }

// Create item icon for the itemgroup.
var itemGroup: ItemGroup? = null

object ItemGroup: Item( Settings() ), ModID {

    private val id = modID("item_group")

    init {

        itemGroup = FabricItemGroup.builder(id)!!
            .icon { defaultStack }.build()

    }

}

fun defaultSettings(): Item.Settings {

    return Item.Settings().maxCount(1)

}

interface StackMenu {

    fun canOpenMenu( user: PlayerEntity, stack: ItemStack ): Boolean {

        val stack2 = user.handItems.find { it != stack }!!

        return stack2.item is AirBlockItem

    }

}

private interface Trackable {


    fun checkHolder( stack: ItemStack, holder: Entity ) {

        if ( stack.holder == holder ) return

        stack.holder = holder

    }

    fun tick( stack: ItemStack, world: World, entity: Entity, slot: Int ) {

        checkHolder(stack, entity)

        if ( world.isClient ) return;     trackTick( stack, slot )

        if ( !NBT.has(stack) ) setupNBT(stack);     val nbt = NBT.get(stack)

        if ( nbt.contains("BlockPos") ) {

            nbt.remove("BlockPos");     nbt.remove("Slot")

        }

    }

    fun trackTick( stack: ItemStack, slot: Int ) {}

    fun getSetupNBT(stack: ItemStack): NbtCompound

    fun setupNBT(stack: ItemStack) {

        val nbt = stack.nbt!!;      nbt.put( MOD_NAME, getSetupNBT(stack) )

    }

}

abstract class ToolItem( material: ToolMaterial, settings: Settings ) : ToolItem( material, settings ), Trackable, ModID {

    override fun allowNbtUpdateAnimation(
        player: PlayerEntity, hand: Hand, oldStack: ItemStack, newStack: ItemStack
    ): Boolean { return false }

    override fun inventoryTick(
        stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean
    ) { tick( stack, world, entity, slot ) }

}

abstract class Item(settings: Settings) : Item(settings), Trackable, ModID {

    override fun allowNbtUpdateAnimation(
        player: PlayerEntity, hand: Hand, oldStack: ItemStack, newStack: ItemStack
    ): Boolean { return false }

    override fun inventoryTick(
        stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean
    ) { tick( stack, world, entity, slot ) }

}

object Fuel {

    private fun register( kclass: KClass<*>, time: Int ) {
        FuelRegistry.INSTANCE.add( modItem(kclass), time )
    }

    fun register() {

        register( MusicalStorage::class, 6000 );    register( Harp::class, 2200 )

        register( AcousticGuitar::class, 2200 );    register( Banjo::class, 2200 )

        register( Cello::class, 3000 );             register( Marimba::class, 4000 )

        register( ElectricGuitar::class, 5500 );    register( ElectricGuitarClean::class, 5500 )

        register( Recorder::class, 600 );           register( Xylophone::class, 4000 )

    }

}