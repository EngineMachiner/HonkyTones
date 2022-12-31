package com.enginemachiner.honkytones.mixins.enchantments;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import com.google.common.collect.Multimap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {

    /** Allow vanilla enchantments to Instruments */
    @Inject(at = @At("HEAD"), method = "isAcceptableItem", cancellable = true)
    private void honkyTonesAllowEnchantments( ItemStack stack,
                                              CallbackInfoReturnable<Boolean> callback ) {

        Multimap<Enchantment, Integer> enchants = Instrument.Companion.getEnchants();
        Enchantment enchantment = ( Enchantment ) ( Object ) this;

        boolean itemCanPlay = stack.getItem() instanceof Instrument;
        boolean isInstrumentEnchant = enchants.containsKey(enchantment);
        if ( itemCanPlay && isInstrumentEnchant ) callback.setReturnValue(true);

    }

}
