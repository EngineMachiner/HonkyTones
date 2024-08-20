package com.enginemachiner.honkytones.datagen

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

class DataGen : DataGeneratorEntrypoint {

    override fun onInitializeDataGenerator( generator: FabricDataGenerator ) {

        val pack = generator.createPack()

        pack.addProvider( ::Advancements )
        pack.addProvider( Tags::Item )

    }

}