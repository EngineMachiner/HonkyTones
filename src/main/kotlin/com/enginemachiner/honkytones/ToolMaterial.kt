package com.enginemachiner.honkytones

import net.minecraft.item.Items
import net.minecraft.item.ToolMaterial
import net.minecraft.item.ToolMaterials
import net.minecraft.recipe.Ingredient

class MusicalString : MusicalQuartz() {
    override val tweak = - 450f
    override fun getRepairIngredient(): Ingredient { return Ingredient.ofItems(Items.STRING) }
}

class MusicalIron : MusicalQuartz() {
    override val tweak = 50f
    override fun getRepairIngredient(): Ingredient { return Ingredient.ofItems(Items.IRON_INGOT) }
}

class MusicalRedstone : MusicalQuartz() {
    override val tweak = 325f
    override fun getRepairIngredient(): Ingredient { return Ingredient.ofItems(Items.REDSTONE_BLOCK) }
}

open class MusicalQuartz : ToolMaterial {

    private val iron = ToolMaterials.IRON
    open val tweak = 150f
    override fun getDurability(): Int { return iron.durability + tweak.toInt() }
    override fun getMiningSpeedMultiplier(): Float { return iron.miningSpeedMultiplier + tweak * 0.0035f }
    override fun getAttackDamage(): Float { return iron.attackDamage + tweak * 0.0015f }
    override fun getMiningLevel(): Int { return ToolMaterials.WOOD.miningLevel }
    override fun getEnchantability(): Int { return iron.enchantability + ( tweak * 0.02 ).toInt() }
    override fun getRepairIngredient(): Ingredient { return Ingredient.ofItems(Items.QUARTZ) }

}