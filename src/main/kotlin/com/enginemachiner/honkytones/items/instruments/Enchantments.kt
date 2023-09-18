package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.ModID
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentTarget
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack

class RangedEnchantment : Enchantment(
    Rarity.UNCOMMON, EnchantmentTarget.WEAPON, EquipmentSlot.values()
), ModID {

    override fun isAcceptableItem( stack: ItemStack? ): Boolean {
        return stack!!.item is Instrument
    }

}