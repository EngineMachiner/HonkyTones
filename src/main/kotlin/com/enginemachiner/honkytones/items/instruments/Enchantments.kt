package com.enginemachiner.honkytones.items.instruments

import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentTarget
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack

object Enchantments

class RangedEnchantment : Enchantment(
    Rarity.RARE, EnchantmentTarget.BREAKABLE,
    EquipmentSlot.values()
) {
    override fun isAcceptableItem(stack: ItemStack?): Boolean {
        return stack!!.item is Instrument
    }
}