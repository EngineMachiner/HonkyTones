package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import kotlin.math.roundToInt
import kotlin.reflect.KClass

// TODO: Check all the slots order. First player inventory, then everything else.

/** It's a custom screen handler that can allow only specific item types to the slots. */
abstract class StrictSlotScreen( type: ScreenHandlerType<*>, syncID: Int ) : ScreenHandler( type, syncID ) {

    fun isAllowed( slotStack: ItemStack, cursorStack: ItemStack, kclass: KClass<*>, range: Boolean ): Boolean {
        return kclass.isInstance( slotStack.item ) || kclass.isInstance( cursorStack.item ) && range
    }

}

// TODO: Check and re-do screen widgets. Specially createButton().

@Environment(EnvType.CLIENT)
class MidiChannelField( textRenderer: TextRenderer, x: Int, y: Int, w: Int, h: Int ) : TextFieldWidget(
    textRenderer, x, y, w, h, Text.of("Midi Channel Field")
) {

    init { setMaxLength(2) }

    override fun tick() {

        // Only allow 1-16 as input.

        val s = text

        val b1 = s.isBlank() && !isFocused;      var b3 = s.length == 2

        val b2 = s.length == 1 && s.contains( Regex("[^1-9]") )

        if (b3) b3 = s[0] != '1' || "${ s[1] }".contains( Regex("[^0-6]") )

        if ( b1 || b2 || b3 ) text = "1";       super.tick()

    }

}

@Environment(EnvType.CLIENT)
class CustomSlider(
    x: Int, y: Int, w: Int, h: Int, private val name: String, float: Float,
) : SliderWidget( x, y, w, h, Text.of("Custom Slider"), float.toDouble() ) {

    init { updateMessage() }

    override fun updateMessage() {

        val int = ( value * 100 ).roundToInt()

        message = Text.of("$name: $int%")

    }

    override fun applyValue() {}

}

fun createButton(
    x: Int, y: Int, x2: Float, y2: Float,
    w: Int, h: Int, w2: Int, w3: Float,
    function: ( button: ButtonWidget ) -> Unit
): ButtonWidget {

    val builder = ButtonWidget.Builder( Text.of("Custom Button") ) { function(it) }

    builder.dimensions(
        ( x + w * 0.5f + w2 * 0.05f + x2 ).toInt(),
        ( y + h * 1.5f + y2 ).toInt(),
        ( w2 + w3 ).toInt(),     ( h * 1.1f ).toInt(),
    )

    return builder.build()

}

@Environment(EnvType.CLIENT)
fun currentScreen(): Screen? { return client().currentScreen }

@Environment(EnvType.CLIENT)
fun isOnScreen(): Boolean { return currentScreen() != null }

object Screen {

    fun networking() {

        val id = modID("close_screen")
        ServerPlayNetworking.registerGlobalReceiver(id) {

            server: MinecraftServer, player: ServerPlayerEntity,
            _: ServerPlayNetworkHandler, _: PacketByteBuf, _: PacketSender ->

            server.send( ServerTask( server.ticks ) {
                player.onHandledScreenClosed();    player.closeHandledScreen()
            } )

        }

    }

}
