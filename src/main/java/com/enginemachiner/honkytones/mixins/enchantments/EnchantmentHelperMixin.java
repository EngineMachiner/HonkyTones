package com.enginemachiner.honkytones.mixins.enchantments;

import com.enginemachiner.honkytones.Verify;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;

@Mixin( EnchantmentHelper.class )
public class EnchantmentHelperMixin {

    /** Allow vanilla enchantments to the instruments in the enchanting pool. */
    @Verify( reason = "Relies on vanilla method." )
    @Inject( at = @At("RETURN"), method = "getPossibleEntries", cancellable = true )
    private static void honkyTonesTweakEnchantingEntries(
            int power, ItemStack stack, boolean treasureAllowed,
            CallbackInfoReturnable< List<EnchantmentLevelEntry> > callback
    ) {

        boolean isInstrument = stack.getItem() instanceof Instrument;
        if ( !isInstrument ) { callback.cancel(); return; }

        Multimap<Enchantment, Integer> enchants = Instrument.Companion.getEnchants();

        // Vanilla:

        List<EnchantmentLevelEntry> list = Lists.newArrayList();
        Item item = stack.getItem();
        boolean bl = stack.isOf(Items.BOOK);
        Iterator var6 = Registries.ENCHANTMENT.iterator();

        while(true) {
            while(true) {
                Enchantment enchantment;
                do {
                    do {
                        do {
                            if (!var6.hasNext()) {
                                callback.setReturnValue(list); return; //
                            }

                            enchantment = (Enchantment)var6.next();
                        } while(enchantment.isTreasure() && !treasureAllowed);
                    } while(!enchantment.isAvailableForRandomSelection());
                } while( !enchantment.type.isAcceptableItem(item) && !bl && !enchants.containsKey(enchantment) ); //

                for(int i = enchantment.getMaxLevel(); i > enchantment.getMinLevel() - 1; --i) {
                    if (power >= enchantment.getMinPower(i) && power <= enchantment.getMaxPower(i)) {
                        list.add(new EnchantmentLevelEntry(enchantment, i));
                        break;
                    }
                }
            }
        }

    }

}