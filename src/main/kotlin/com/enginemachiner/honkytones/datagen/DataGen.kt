package com.enginemachiner.honkytones.datagen

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

class DataGen : DataGeneratorEntrypoint {

    override fun onInitializeDataGenerator( generator: FabricDataGenerator ) {

        generator.addProvider(::Advancements)
        generator.addProvider( Tags::Item )

    }

}