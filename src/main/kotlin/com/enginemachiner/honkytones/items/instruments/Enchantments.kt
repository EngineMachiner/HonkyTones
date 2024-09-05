package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.harmony.ModID
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentTarget
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack

class RangedEnchantment : Enchantment( Rarity.RARE, EnchantmentTarget.WEAPON, slotTypes ), ModID {

    override fun isAcceptableItem(stack: ItemStack): Boolean { return stack.item is InstrumentItem }

    companion object {

        private val slotTypes = EquipmentSlot.entries.toTypedArray()

        lateinit var registered: Enchantment

    }

}