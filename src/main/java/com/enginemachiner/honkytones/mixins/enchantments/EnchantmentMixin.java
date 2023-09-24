package com.enginemachiner.honkytones.mixins.enchantments;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import com.google.common.collect.Multimap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( Enchantment.class )
public class EnchantmentMixin {

    /** Allow vanilla enchantments to instruments. */
    @Inject( at = @At("HEAD"), method = "isAcceptableItem", cancellable = true )
    private void honkyTonesEnableVanillaEnchantments( ItemStack stack, CallbackInfoReturnable<Boolean> callback ) {

        Multimap<Enchantment, Integer> enchants = Instrument.Companion.getEnchants();
        Enchantment enchantment = (Enchantment) (Object) this;

        boolean isInstrument = stack.getItem() instanceof Instrument;
        boolean isEnchantment = enchants.containsKey(enchantment);
        if ( isInstrument && isEnchantment ) callback.setReturnValue(true);

    }

}
