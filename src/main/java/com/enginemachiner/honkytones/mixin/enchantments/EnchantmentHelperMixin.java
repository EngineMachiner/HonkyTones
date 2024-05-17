package com.enginemachiner.honkytones.mixin.enchantments;

import com.enginemachiner.harmony.BasedOn;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import com.google.common.collect.Lists;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin( EnchantmentHelper.class )
public class EnchantmentHelperMixin {

    /** Allow vanilla enchantments to the instruments in the enchanting pool. */
    @BasedOn( reason = "Relies on the vanilla method." )
    @Inject( at = @At("RETURN"), method = "getPossibleEntries", cancellable = true )
    private static void honkyTonesEnchantEntries(
            int power, ItemStack stack, boolean treasureAllowed,
            CallbackInfoReturnable< List<EnchantmentLevelEntry> > callback
    ) {

        boolean isInstrument = stack.getItem() instanceof Instrument;
        if ( !isInstrument ) { callback.cancel(); return; }

        List<Enchantment> enchantments = Instrument.Companion.getEnchantments();

        // Next code is based on the former method.

        ArrayList<EnchantmentLevelEntry> list = Lists.newArrayList();
        boolean isBook = stack.isOf( Items.BOOK );      Item item = stack.getItem();

        block0: for ( Enchantment enchantment : Registry.ENCHANTMENT ) {

            boolean b1 = enchantment.type.isAcceptableItem(item)
                    || enchantments.contains(enchantment);

            boolean b2 = enchantment.isTreasure() && !treasureAllowed
                    || !enchantment.isAvailableForRandomSelection()
                    || !b1 && !isBook;

            if (b2) continue;

            for ( int i = enchantment.getMaxLevel(); i > enchantment.getMinLevel() - 1; --i ) {

                if ( power < enchantment.getMinPower(i) || power > enchantment.getMaxPower(i) ) continue;

                list.add( new EnchantmentLevelEntry(enchantment, i) );

                continue block0;

            }

        }

        callback.setReturnValue(list);

    }

}