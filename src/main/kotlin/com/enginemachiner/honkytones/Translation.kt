package com.enginemachiner.honkytones

import net.minecraft.text.TranslatableText

object Translation {
    fun get ( key: String ): String { return TranslatableText(key).string }
}

