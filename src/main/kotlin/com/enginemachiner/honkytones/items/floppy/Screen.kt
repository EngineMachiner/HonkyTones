package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.NBT.keepDisplay
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.DrawableHelper
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.Clipboard
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

@Environment(EnvType.CLIENT)
class FloppyDiskScreen( private val stack: ItemStack ) : Screen( Text.of(screenTitle) ) {

    private var searchField: TextFieldWidget? = null

    private var copyButton: ButtonWidget? = null
    private var clearButton: ButtonWidget? = null

    private val nbt = NBT.get(stack)
    private var path = nbt.getString("Path")
    private val lastPath = path

    override fun shouldPause(): Boolean { return false }

    override fun onClose() {

        path = searchField!!.text

        if ( lastPath == path ) { super.onClose(); return }

        // Reset volume and rate.

        nbt.putFloat( "Rate", 1f );     nbt.putFloat( "Volume", 1f )

        nbt.remove("hasRequestDisplay")

        if ( path.isNotBlank() ) {

            if ( isValidUrl(path) ) {

                nbt.putBoolean( "onQuery", true )

                stack.setCustomName( Text.of(path) )

            } else {

                val title = path

                if ( !path.startsWith(MOD_NAME) ) path = "$MOD_NAME/$path"

                if ( ModFile(path).isFile ) stack.setCustomName( Text.of(title) )

            }

        } else nbt.putBoolean( "resetDisplay", true )

        val times = nbt.getInt("timesWritten") + 1

        nbt.putInt( "timesWritten", times )

        keepDisplay( stack, nbt );      nbt.putString( "Path", path )

        networkNBT(nbt);                super.onClose()

    }

    override fun init() {

        val x = ( width * 0.5 - width * 0.75 * 0.5 ).toInt()
        val y = ( height * 0.2 * 1.5 ).toInt()
        val w = ( width * 0.75 ).toInt()
        val h = ( 240 * 0.08 ).toInt()
        val w2 = ( w * 0.35 ).toInt()


        searchField = TextFieldWidget( textRenderer, x, y, w, h, Text.of(path) )

        searchField!!.setMaxLength(250);        searchField!!.text = path

        addSelectableChild(searchField)


        copyButton = createButton( x, y, - w * 0.4f, 0f, w, h, w2, 0f ) {
            Clipboard().setClipboard( client!!.window.handle, searchField!!.text )
        }

        copyButton!!.message = Text.of(copyTitle)

        addSelectableChild(copyButton)


        clearButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) { searchField!!.text = "" }

        clearButton!!.message = Text.of(clearTitle)

        addSelectableChild(clearButton)


    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices)

        children().forEach { it as Drawable;     it.render( matrices, mouseX, mouseY, delta ) }

        var title = screenTitle
        DrawableHelper.drawCenteredText( matrices, textRenderer, title, ( width * 0.5 ).toInt(), 15, 0xFFFFFF )

        title = stack.name.string
        DrawableHelper.drawCenteredText( matrices, textRenderer, title, ( width * 0.5 ).toInt(), 30, 0xFFFFFF )

        textRenderer.draw( matrices, "$pathTitle:", searchField!!.x.toFloat(), searchField!!.y.toFloat() - 12, 0xFFFFFF )

    }

    companion object {

        private val screenTitle = Translation.item("floppy_disk.title")
        private val pathTitle = Translation.item("gui.path")
        private val copyTitle = Translation.item("gui.copy")
        private val clearTitle = Translation.item("gui.clear")

    }

}