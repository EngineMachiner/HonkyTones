package com.enginemachiner.honkytones.client.items.instruments

import com.enginemachiner.harmony.client.ColorItem
import com.enginemachiner.honkytones.items.instruments.SFX.Companion.registeredItem

object SFX : ColorItem {

    fun registerColorProvider() { registerColorProvider(registeredItem) }

}