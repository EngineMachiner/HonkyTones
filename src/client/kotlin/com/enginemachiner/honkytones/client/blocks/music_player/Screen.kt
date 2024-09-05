package com.enginemachiner.honkytones.client.blocks.music_player

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Translation
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.textureID
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerScreenHandler
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerScreenHandler.Companion.type
import com.enginemachiner.honkytones.client.Silencer.isMuted
import com.enginemachiner.honkytones.items.FloppyDisk
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import kotlin.math.roundToInt

class MusicPlayerScreen(

    handler: MusicPlayerScreenHandler, playerInventory: PlayerInventory, text: Text

) : HarmonyScreen<MusicPlayerScreenHandler>( handler, playerInventory, text ) {

    private var widgetWidth = 0f

    init { titleY -= 9;     playerInventoryTitleY += 9 }

    private val texture = Texture( textureID ) {

        it.setSize( 176f, 186f );       it.center(width, height)

    }

    private val id = handler.id!!

    val musicPlayer = MusicPlayer.get(id)

    init { musicPlayer.update(handler) }

    private fun isPlaying(): Boolean { return musicPlayer.isPlaying }
    private fun onRepeat(): Boolean { return musicPlayer.onRepeat }
    private fun pos(): BlockPos? { return musicPlayer.pos }

    private var listenButton: Button? = null
    private var volumeSlider: VolumeSlider? = null

    var repeatButton: Button? = null
    var triggerButton: Button? = null

    private val states = mutableMapOf( true to Translations.on,     false to Translations.off )

    private val triggerStates = mutableMapOf( true to Translations.play,        false to Translations.stop )

    private fun isListening(): Boolean { return musicPlayer.isListening }

    private fun listenMessage(): String {

        val title = Translations.listen;        val value = states[ isListening() ]

        return "$title: $value"

    }

    private fun repeatMessage(): String {

        val title = Translations.repeat;        val value = states[ onRepeat() ]

        return "$title: $value"

    }

    private fun triggerMessage(): String {

        return triggerStates[ !isPlaying() ]!!

    }

    fun listen() {

        if ( isListening() ) return;        listenButton!!.onPress()

    }

    override fun shouldPause(): Boolean { return false }

    override fun init() {

        super.init();       texture.init()


        widgetWidth = width * 0.2f;        val w = widgetWidth


        var x = width - texture.w - w - 35

        x *= 0.5f;      val h = 20f

        volumeSlider = VolumeSlider( x, h * 2f, w, h, musicPlayer )

        listenButton = Button( x, h * 3.5f, w, h, ::listenMessage ) {

            musicPlayer.isListening = !isListening()

            musicPlayer.sendState( "set_user_state", isListening() )

            it.updateMessage();         refresh()

        }

        repeatButton = Button( x, h * 5f, w, h, ::repeatMessage ) {

            musicPlayer.sendState( "set_repeat", !onRepeat() )

            refresh()

        }

        triggerButton = Button( x, h * 6.5f, w, h, ::triggerMessage ) {

            listen();       val pos = pos()!!


            val netID = MusicPlayer.netID("trigger")

            val sender = Sender(netID) { it.write(pos) }

            sender.toServer()

        }

        val widgets = mutableListOf( listenButton, repeatButton )

        if ( handler.hasRemote() ) widgets.add( triggerButton )

        widgets.forEach { addDrawableChild(it) }

        addSlider( volumeSlider!! )

    }

    private fun refresh() { listenButton!!.updateMessage() }

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        texture.draw(matrices)

    }

    override fun handledScreenTick() {

        val volumeSlider = volumeSlider ?: return

        volumeSlider.tick()

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean {

        return texture.isClickOutsideBounds(mouseX, mouseY)

    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {

        return isFocusedDragged(mouseX, mouseY, button, deltaX, deltaY)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {

        return wasElementDragged(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button)

    }

    companion object : ModID {

        private val textureID = textureID("block/music_player/slots.png")

        fun register() { HandledScreens.register( type, ::MusicPlayerScreen ) }

        private object Translations {

            val on = Translation.get("gui.on")
            val off = Translation.get("gui.off")

            val play = Translation.item("remote.play")
            val stop = Translation.item("remote.stop")

            val volume = Translation.item("gui.volume")
            val listen = Translation.block("music_player.listen")

            val repeat = Translation.block("music_player.repeat")

        }

        private class VolumeSlider(

            x: Float, y: Float, w: Float, h: Float,

            private val musicPlayer: MusicPlayer,

            ) : Slider( x, y, w, h ) {

            init { init() }

            private fun init() {

                visible = false;        if ( !visible() ) return

                value = volume();       updateMessage()

            }

            private fun visible(): Boolean {

                val check = musicPlayer.isListening && musicPlayer.hasInput()

                val entity = musicPlayer.entity() ?: return check

                return check && !isMuted(entity)

            }

            private fun volume(): Double { return settings().getDouble("Volume") }

            private fun nbt(): NbtCompound { return nbt( floppy() ) }

            private fun settings(): NbtCompound {

                return FloppyDisk.settings( floppy(), player() )

            }

            private fun floppy(): ItemStack { return musicPlayer.floppy() }

            fun tick() { visible = visible();        check() }

            private fun check() {

                if ( !visible || value == volume() ) return

                value = volume();       updateMessage()

            }

            override fun applyValue() {

                settings().putDouble( "Volume", value )

                send( nbt() )

            }

            override fun format( value: Double ): String {

                val title = Translations.volume

                val i = ( value * 100 ).roundToInt()

                return "$title: $i%"

            }

        }

    }

}
