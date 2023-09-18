package com.enginemachiner.honkytones.items.instruments

import net.minecraft.item.Items
import net.minecraft.item.ToolMaterial
import net.minecraft.item.ToolMaterials
import net.minecraft.recipe.Ingredient

class MusicalString : InstrumentMaterial() {

    override val data = mapOf<String, Any>(
        "Durability" to ( ToolMaterials.WOOD.durability * 1.25f ).toInt(),
        "Mining Speed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "Attack Damage" to ToolMaterials.WOOD.attackDamage + 1.25f,
        "Mining Level" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 4,
        "Repair Ingredient" to Ingredient.ofItems( Items.STRING )
    )

}

class MusicalIron : InstrumentMaterial() {

    override val data = mapOf(
        "Durability" to ( ToolMaterials.IRON.durability * 1.25f ).toInt(),
        "Mining Speed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "Attack Damage" to ToolMaterials.IRON.attackDamage + 0.25f,
        "Mining Level" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 1,
        "Repair Ingredient" to Ingredient.ofItems( Items.IRON_INGOT )
    )

}

class MusicalQuartz : InstrumentMaterial() {

    override val data = mapOf(
        "Durability" to ( ToolMaterials.IRON.durability * 1.5f ).toInt(),
        "Mining Speed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "Attack Damage" to ToolMaterials.IRON.attackDamage + 0.5f,
        "Mining Level" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 2,
        "Repair Ingredient" to Ingredient.ofItems( Items.QUARTZ_BLOCK )
    )

}

class MusicalRedstone : InstrumentMaterial() {

    override val data = mapOf(
        "Durability" to ToolMaterials.IRON.durability * 2,
        "Mining Speed" to ToolMaterials.WOOD.miningSpeedMultiplier * 6,
        "Attack Damage" to ToolMaterials.IRON.attackDamage + 0.75f,
        "Mining Level" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 3,
        "Repair Ingredient" to Ingredient.ofItems( Items.REDSTONE_BLOCK )
    )

}

abstract class InstrumentMaterial : ToolMaterial {

    protected abstract val data: Map<String, Any>

    override fun getDurability(): Int { return data["Durability"] as Int }
    override fun getMiningSpeedMultiplier(): Float { return data["Mining Speed"] as Float }
    override fun getAttackDamage(): Float { return data["Attack Damage"] as Float }
    override fun getMiningLevel(): Int { return data["Mining Level"] as Int }
    override fun getEnchantability(): Int { return data["Enchantability"] as Int }
    override fun getRepairIngredient(): Ingredient { return data["Repair Ingredient"] as Ingredient }

}