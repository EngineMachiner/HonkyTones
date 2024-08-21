package com.enginemachiner.honkytones.datagen

import com.enginemachiner.harmony.modID
import com.enginemachiner.harmony.modItem
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.tag.TagKey
import java.util.concurrent.CompletableFuture

// TODO: Instrument type tags?

private typealias Wrapper = RegistryWrapper.WrapperLookup

object Tags {

    val instruments = TagKey.of( RegistryKeys.ITEM, modID("instruments") )!!

    class Item( output: FabricDataOutput, wrapper: CompletableFuture<Wrapper> ) : FabricTagProvider.ItemTagProvider(output, wrapper) {

        override fun configure( wrapper: Wrapper ) {

            val builder = getOrCreateTagBuilder(instruments)

            for ( kClass in InstrumentItem.classes ) builder.add( modItem(kClass) )

        }

    }

}