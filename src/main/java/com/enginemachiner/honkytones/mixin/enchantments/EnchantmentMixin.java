package com.enginemachiner.honkytones.mixin.enchantments;

import com.enginemachiner.honkytones.items.instruments.InstrumentItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@SuppressWarnings("UnreachableCode")
@Mixin( Enchantment.class )
public class EnchantmentMixin {

    /** Allow vanilla enchantments to instruments. */
    @Inject( at = @At("HEAD"), method = "isAcceptableItem", cancellable = true )
    private void honkyTonesEnableVanillaEnchantments( ItemStack stack, CallbackInfoReturnable<Boolean> callback ) {

        List<Enchantment> enchantments = InstrumentItem.Companion.getEnchantments();
        Enchantment enchantment = (Enchantment) (Object) this;

        boolean isInstrument = stack.getItem() instanceof InstrumentItem;
        boolean isEnchantment = enchantments.contains(enchantment);
        if ( isInstrument && isEnchantment ) callback.setReturnValue(true);

    }

}
