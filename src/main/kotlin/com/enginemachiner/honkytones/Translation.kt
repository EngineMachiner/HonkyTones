package com.enginemachiner.honkytones

import net.minecraft.text.Text

object Translation {
    fun get ( key: String ): String { return Text.translatable(key).string }
}

