package com.enginemachiner.honkytones

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.sound.SoundCategory
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.util.Identifier

// Client screen

class Menu : Screen( LiteralText("HonkyTones") ) {

    private var textfield: TextFieldWidget? = null
    private var clearButton: ButtonWidget? = null
    private var stateButton: ButtonWidget? = null
    private val inst = MinecraftClient.getInstance().player!!.mainHandStack.item as Instrument
    private val states = setOf("Attack","Play","Push")
    private val sounds = setOf(
        getSoundInstance("honkytones:drumset-g4"),
        getSoundInstance("honkytones:keyboard-c3"),
        getSoundInstance("honkytones:trombone-g3")
    )
    private val clickSound = getSoundInstance("ui.button.click")

    override fun onClose() {

        super.onClose()
        inst.noteSequence = textfield!!.text
        inst.sequenceSub = ""

        // Send data to the server
        // I didn't want to use a shared screen for this

        val buf = PacketByteBufs.create()
        buf.writeString(textfield!!.text + " Action: " + inst.state)
        ClientPlayNetworking.send( Identifier(netID + "client-screen"), buf )

    }

    override fun init() {

        for ( id in client!!.soundManager.keys ) {
            if ( id.namespace.contains("honkytones") ) {
                client!!.soundManager.stopSounds( id, SoundCategory.PLAYERS )
            }
        }

        var stateIndex = states.indexOf(inst.state)

        for ( sound in sounds ) {
            sound.volume = client!!.options.getSoundVolume(SoundCategory.PLAYERS)
        }
        clickSound.volume = client!!.options.getSoundVolume(SoundCategory.MASTER)

        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.08 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()

        textfield = TextFieldWidget(
            textRenderer,
            x, y,
            w, h,
            Text.of("")
        )
        textfield!!.setMaxLength(32 * 5)
        textfield!!.text = inst.noteSequence
        addSelectableChild(textfield)

        val w2 = (w * 0.35).toInt()
        clearButton = ButtonWidget(
            (x + w * 0.5 + w2 * 0.05).toInt(), (y + h * 1.5).toInt(),
            w2, (h * 1.1).toInt(),
            Text.of("Clear")
        ) {
            textfield!!.text = ""
        }

        addSelectableChild(clearButton)

        stateButton = ButtonWidget(
            (x + w * 0.5 - w2 - w2 * 0.05).toInt(), (y + h * 1.5).toInt(),
            w2, (h * 1.1).toInt(),
            Text.of("")
        ) {
            stateIndex++
            if (stateIndex > states.size - 1) { stateIndex = 0 }
            inst.state = states.elementAt(stateIndex)
            stateButton!!.message = Text.of("Action: " + inst.state)
        }
        stateButton!!.message = Text.of("Action: " + inst.state)
        addSelectableChild(stateButton)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        textfield!!.render(matrices, mouseX, mouseY, delta)
        clearButton!!.render(matrices, mouseX, mouseY, delta)
        stateButton!!.render(matrices, mouseX, mouseY, delta)
    }

}

/*
// Shared screen (CLIENT-SERVER)

// These two belong to each other
// First create your type of screen, then the handler
val MENU: ScreenHandlerType<MenuScreenHandler> = ScreenHandlerRegistry.registerSimple(
    Identifier(Base.MOD_ID, "honkytones-menu")
) { syncID: Int, _: PlayerInventory? -> MenuScreenHandler(syncID) }

class MenuScreenHandler(syncId: Int) : ScreenHandler(MENU, syncId) {
    override fun canUse(player: PlayerEntity?): Boolean {
        return player!!.inventory.canPlayerUse(player)
    }
}

// Create my screen
class MenuScreen(
    handler: MenuScreenHandler?, inventory: PlayerInventory?, title: Text?
) : HandledScreen<MenuScreenHandler?>(handler, inventory, title) {
    override fun drawBackground(matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int) {}
}

// Create factory
class MenuScreenFactory : NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory?, player: PlayerEntity?): ScreenHandler {
        return MenuScreenHandler(syncId)
    }
    override fun getDisplayName(): Text { return LiteralText("HonkyTones") }
}

class Menu : ClientModInitializer {
    override fun onInitializeClient() {
        // Screens
        ScreenRegistry.register(MENU) {
                honkyTonesScreenHandler: MenuScreenHandler,
                playerInventory: PlayerInventory, text: Text ->
            MenuScreen(honkyTonesScreenHandler, playerInventory, text)
        }
    }
}

*/