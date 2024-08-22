package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.client.TextField
import net.minecraft.client.font.TextRenderer

class MidiChannelField(

    x: Float, y: Float, w: Float, h: Float,

    message: String, renderer: TextRenderer, init: (TextField) -> Unit = {}

) : TextField( x, y, w, h, message, renderer, init ) {

    init { setMaxLength(2) }

    override fun tick() { checkField();   super.tick() }

    private fun isValid(): Boolean {

        return text.matches( Regex("1[0-6]?") )
                || text.matches( Regex("[1-9]") )

    }

    fun checkField() {

        if ( !isValid() && !isFocused ) text = "1"

    }

}