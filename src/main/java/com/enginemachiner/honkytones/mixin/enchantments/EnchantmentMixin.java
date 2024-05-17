package com.enginemachiner.honkytones.mixin.enchantments;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin( Enchantment.class )
public class EnchantmentMixin {

    /** Allow vanilla enchantments to instruments. */
    @Inject( at = @At("HEAD"), method = "isAcceptableItem", cancellable = true )
    private void honkyTonesEnableVanillaEnchantments( ItemStack stack, CallbackInfoReturnable<Boolean> callback ) {

        List<Enchantment> enchantments = Instrument.Companion.getEnchantments();
        Enchantment enchantment = (Enchantment) (Object) this;

        boolean isInstrument = stack.getItem() instanceof Instrument;
        boolean isEnchantment = enchantments.contains(enchantment);
        if ( isInstrument && isEnchantment ) callback.setReturnValue(true);

    }

}
