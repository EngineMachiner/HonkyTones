package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.honkytones.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.Clipboard
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class FloppyDiskScreen( private val stack: ItemStack )
    : Screen( Text.of("") ) {

    private var searchField: TextFieldWidget? = null
    private var copyButton: ButtonWidget? = null
    private var clearButton: ButtonWidget? = null

    private val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
    private var path = nbt.getString("path")
    private val lastPath = path

    override fun shouldPause(): Boolean { return false }

    override fun onClose() {

        path = searchField!!.text

        if ( lastPath == path ) { super.onClose(); return }

        if ( path.isNotBlank() ) {

            if ( Network.isValidUrl(path) ) {

                nbt.putBoolean("yt-dlp", true)
                stack.setCustomName( Text.of( path ) )
                // Check FloppyDisk inventoryTick()

            } else {

                if ( !hasMidiSystemSequencer() ) { super.onClose(); return }

                nbt.remove("yt-dlp")
                nbt.remove("queryInterrupted")

                var prePath = path
                if ( !path.startsWith(Base.MOD_NAME) ) path = Base.MOD_NAME + "/$path"
                if ( !path.endsWith(".mid") ) { path += ".mid";     prePath += ".mid" }
                if ( RestrictedFile(path).isFile ) {
                    stack.setCustomName( Text.of( prePath ) )
                }

            }

        } else nbt.putBoolean("removeName", true)

        val times = nbt.getInt("timesWritten")
        nbt.putInt( "timesWritten", times + 1 )

        if ( times + 1 > nbt.getInt("seed") ) nbt.putBoolean( "isDone", true )

        writeDisplayOnNbt( stack, nbt )

        nbt.putString("path", path)
        Network.sendNbtToServer(nbt)

        super.onClose()

    }

    override fun init() {

        screenTitle = Translation.get("item.honkytones.floppydisk.title")
        pathTitle = Translation.get("item.honkytones.gui.path")
        copyTitle = Translation.get("item.honkytones.gui.copy")
        clearTitle = Translation.get("item.honkytones.gui.clear")

        val window = client!!.window

        // Based dimensions
        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.2 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()
        val w2 = (w * 0.35).toInt()

        searchField = TextFieldWidget( textRenderer, x, y, w, h, Text.of(path) )
        searchField!!.setMaxLength(75 * 2)
        searchField!!.text = path
        addSelectableChild(searchField)

        copyButton = createButton( x, y, - w * 0.4f, 0f, w, h, w2, 0f ) {
            clipboard.setClipboard( window.handle, searchField!!.text )
        }
        copyButton!!.message = Text.of(copyTitle)
        addSelectableChild(copyButton)

        clearButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) { searchField!!.text = "" }
        clearButton!!.message = Text.of(clearTitle)
        addSelectableChild(clearButton)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)

        searchField!!.render(matrices, mouseX, mouseY, delta)
        copyButton!!.render(matrices, mouseX, mouseY, delta)
        clearButton!!.render(matrices, mouseX, mouseY, delta)

        var s = screenTitle
        textRenderer.draw( matrices, s, width * 0.5f - s.length * 0.5f * 5.9f,
            15f, 0xFFFFFF
        )

        s = stack.name.string
        textRenderer.draw( matrices, s, width * 0.5f - s.length * 0.5f * 5.9f,
            30f, 0xFFFFFF
        )

        textRenderer.draw(
            matrices, "$pathTitle:",
            searchField!!.x.toFloat(), searchField!!.y.toFloat() - 12,
            0xFFFFFF
        )

    }

    companion object {
        private var screenTitle = ""
        private var pathTitle = ""
        private var copyTitle = ""
        private var clearTitle = ""
        private val clipboard = Clipboard()
    }

}