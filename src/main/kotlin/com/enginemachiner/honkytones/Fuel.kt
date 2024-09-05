package com.enginemachiner.honkytones

import com.enginemachiner.harmony.Register
import com.enginemachiner.honkytones.items.instruments.*
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import kotlin.reflect.KClass

object Fuel {

    private val map = mutableMapOf(
        MusicalStorage::class to 6000,          Harp::class to 2200,
        AcousticGuitar::class to 2200,          Banjo::class to 2200,
        Cello::class to 3000,                   Marimba::class to 4000,
        ElectricGuitar::class to 5500,          ElectricGuitarClean::class to 5500,
        Recorder::class to 600,                 Xylophone::class to 4000
    )

    private fun register( kClass: KClass<*>, time: Int ) { Register.fuel( kClass, time ) }

    fun register() { map.forEach { (kClass, time) -> register(kClass, time) } }

}