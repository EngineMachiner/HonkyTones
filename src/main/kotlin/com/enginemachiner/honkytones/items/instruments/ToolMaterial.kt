package com.enginemachiner.honkytones.items.instruments

import net.minecraft.item.Items
import net.minecraft.item.ToolMaterial
import net.minecraft.item.ToolMaterials
import net.minecraft.recipe.Ingredient

class MusicalString : MusicalRedstone() {

    override var data = mapOf<String, Any>(
        "Durability" to (ToolMaterials.WOOD.durability * 1.25f).toInt(),
        "MiningSpeed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "AttackDamage" to ToolMaterials.WOOD.attackDamage + 1.25f,
        "MiningLevel" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 4,
        "RepairIngredient" to Ingredient.ofItems(Items.STRING)
    )

}

class MusicalIron : MusicalRedstone() {

    override var data = mapOf<String, Any>(
        "Durability" to (ToolMaterials.IRON.durability * 1.25f).toInt(),
        "MiningSpeed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "AttackDamage" to ToolMaterials.IRON.attackDamage + 0.25f,
        "MiningLevel" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 1,
        "RepairIngredient" to Ingredient.ofItems(Items.IRON_INGOT)
    )

}

class MusicalQuartz : MusicalRedstone() {

    override var data = mapOf<String, Any>(
        "Durability" to (ToolMaterials.IRON.durability * 1.5f).toInt(),
        "MiningSpeed" to ToolMaterials.WOOD.miningSpeedMultiplier,
        "AttackDamage" to ToolMaterials.IRON.attackDamage + 0.5f,
        "MiningLevel" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 2,
        "RepairIngredient" to Ingredient.ofItems(Items.QUARTZ_BLOCK)
    )

}

open class MusicalRedstone : ToolMaterial {

    open var data = mapOf(
        "Durability" to ToolMaterials.IRON.durability * 2,
        "MiningSpeed" to ToolMaterials.WOOD.miningSpeedMultiplier * 6,
        "AttackDamage" to ToolMaterials.IRON.attackDamage + 0.75f,
        "MiningLevel" to ToolMaterials.WOOD.miningLevel,
        "Enchantability" to ToolMaterials.IRON.enchantability + 3,
        "RepairIngredient" to Ingredient.ofItems(Items.REDSTONE_BLOCK)
    )

    override fun getDurability(): Int { return data["Durability"] as Int }
    override fun getMiningSpeedMultiplier(): Float { return data["MiningSpeed"] as Float }
    override fun getAttackDamage(): Float { return data["AttackDamage"] as Float }
    override fun getMiningLevel(): Int { return data["MiningLevel"] as Int }
    override fun getEnchantability(): Int { return data["Enchantability"] as Int }
    override fun getRepairIngredient(): Ingredient { return data["RepairIngredient"] as Ingredient }

}