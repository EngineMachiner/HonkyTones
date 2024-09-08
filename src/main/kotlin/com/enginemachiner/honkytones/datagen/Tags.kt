package com.enginemachiner.honkytones.datagen

import com.enginemachiner.harmony.modID
import com.enginemachiner.harmony.modItem
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider
import net.fabricmc.fabric.api.tag.TagFactory

// TODO: Instrument type tags?

object Tags {

    val instruments = TagFactory.ITEM.create( modID("instruments") )!!

    class Item( generator: FabricDataGenerator ) : FabricTagProvider.ItemTagProvider(generator) {

        override fun generateTags() {

            val builder = getOrCreateTagBuilder(instruments)

            for ( kClass in InstrumentItem.classes ) builder.add( modItem(kClass) )

        }

    }

}