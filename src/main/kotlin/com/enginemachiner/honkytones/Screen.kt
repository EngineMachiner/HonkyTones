package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.Item
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.text.Text
import kotlin.math.roundToInt
import kotlin.reflect.KClass

abstract class SpecialSlotScreenHandler(type: ScreenHandlerType<*>,
                                        syncID: Int ) : ScreenHandler( type, syncID ) {

    fun isAllowed(slotItem: Item, cursorItem: Item,
                  classA: KClass<*>, range: Boolean ): Boolean {

        var b = classA.isInstance(slotItem) || classA.isInstance(cursorItem)
        b = b && range

        return b

    }

}

@Environment(EnvType.CLIENT)
class ChannelTextFieldWidget( textRenderer: TextRenderer, x: Int, y: Int, w: Int, h: Int )
    : TextFieldWidget( textRenderer, x, y, w, h, Text.of("") ) {

    init { setMaxLength(2) }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        // Channel input restrictions
        val b1 = text.isBlank() && !isFocused
        val b2 = text.length == 1
                && text.contains(Regex("[^1-9]"))

        val b: Boolean
        var b3 = text.length == 2
        if ( b3 ) {
            b = text[1].toString().contains( Regex("[^0-6]") )
            b3 = text[0] != '1' || b
        }

        if ( b1 || b2 || b3 ) text = "1"

        super.render(matrices, mouseX, mouseY, delta)

    }

}

@Environment(EnvType.CLIENT)
class CustomSlider(
    x: Int, y: Int, w: Int, h: Int,
    private val name: String, float: Float,
) : SliderWidget( x, y, w, h, null, float.toDouble() ) {

    init {
        val int = (value * 100).roundToInt()
        message = Text.of("$name: $int%")
    }

    override fun updateMessage() {
        val int = (value * 100).roundToInt()
        message = Text.of("$name: $int%")
    }

    override fun applyValue() {}

}

fun createButton(
    x: Int, y: Int, x2: Float, y2: Float,
    w: Int, h: Int, w2: Int, w3: Float,
    func: (butt: ButtonWidget) -> Unit
): ButtonWidget {

    return ButtonWidget(
        ( x + w * 0.5 + w2 * 0.05 + x2 ).toInt(),
        ( y + h * 1.5 + y2 ).toInt(),
        ( w2 + w3 ).toInt(),     ( h * 1.1 ).toInt(),
        Text.of("")
    ) { func(it) }

}