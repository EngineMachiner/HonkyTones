package com.enginemachiner.honkytones.items.console

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.sendNBT
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.MIDI
import com.enginemachiner.honkytones.MusicTheory
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandlerFactory
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import org.lwjgl.glfw.GLFW
import java.awt.Color
import javax.sound.midi.*

private val particles = Instrument.Companion.ActionParticles

class DigitalConsoleScreenHandler(

    syncID: Int, private val playerInventory: PlayerInventory, private val inventory: Inventory

) : HarmonyScreenHandler( type, syncID ) {


    constructor( syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, SimpleInventory(1) )

    constructor( stack: ItemStack, syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, StackInventory(stack, 1) )


    private val player = playerInventory.player;        private val world = player.world

    val stack = handItem( player, DigitalConsole::class )


    init {

        checkSize( inventory, inventory.size() );   inventory.onOpen(player)

        val slot = Slot( inventory, 0, 220, 160 )

        addSlot(slot);      checkSlot()

    }

    private fun checkSlot() {

        val current = inventory.getStack(0)

        val contains = playerInventory.contains(current)

        if ( contains || current.isEmpty || world.isClient ) return

        inventory.setStack( 0, ItemStack.EMPTY );       inventory.markDirty()

    }

    override fun close( player: PlayerEntity ) {

        super.close(player);    val stack = inventory.getStack(0)

        if ( stack.isEmpty || !world.isClient ) return

        val instrument = stack.item as Instrument

        instrument.stopSounds(stack)

    }

    override fun canUse( player: PlayerEntity ): Boolean { return true }

    override fun transferSlot( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val factory = PickStackScreenHandler.factory
        val text = Text.of("Pick Stack Screen")

        val screenFactory = SimpleNamedScreenHandlerFactory( factory, text )

        player.openHandledScreen(screenFactory)

    }

    companion object : ModID {

        fun factory( stack: ItemStack ): ScreenHandlerFactory {

            return ScreenHandlerFactory { id, inventory, _ ->

                DigitalConsoleScreenHandler( stack, id, inventory )

            }

        }

        val type = ScreenHandlerType( ::DigitalConsoleScreenHandler )

        fun register() {

            Registry.register( Registry.SCREEN_HANDLER, classID(), type )

            if ( !isClient() ) return

            HandledScreens.register( type, ::DigitalConsoleScreen )

        }

    }

}

// @Environment(EnvType.CLIENT)
class DigitalConsoleScreen(
    handler: DigitalConsoleScreenHandler, playerInventory: PlayerInventory, title: Text
) : HandledScreen<DigitalConsoleScreenHandler>( handler, playerInventory, title ) {

    private val console = handler.stack;        private val nbt = NBT.get(console)

    private val instrument = StackInventory(console, 1).getStack(0)

    private val item = instrument.item


    var path = "";      var channel = 0


    private val slots = handler.slots

    private val slotTexture = Texture( Textures.slot ) {

        it.setSize(32f);        it.setPos( x, y, slots[0] )

    }

    private val consoleBackTexture = Texture( Textures.consoleBack ) {

        it.setSize(256f);       it.center(width, height)

    }

    private lateinit var textures: MutableList<Texture>


    private val octaveText = RenderText {

        it.setPos( x.toFloat(), y + 17f )

    }

    private val time = Time {

        it.center( width, height );       it.offsetY( - 44f )

        it.offsetX(3f)

    }

    private val texts = setOf( octaveText, time )


    var box: RecordingCheckbox? = null

    private fun octave(): Int { return nbt.getInt("Octave") }

    fun record() {

        val box = box ?: return;      if ( !box.isChecked ) return


        val sequencer = sequencer!!

        sequencer.sequence = Sequence( Sequence.PPQ, 10 )

        sequencer.sequence.createTrack()


        val nbt = NBT.get(console);       nbt.putBoolean( "damageStack", true )

        sendNBT(nbt)

    }

    override fun init() {

        instrument.holder = player();        super.init()


        textures = mutableListOf( slotTexture, consoleBackTexture )

        addKeys();      textures.forEach { it.init() }


        val text = Translations.record

        texts.forEach { it.init(textRenderer) }


        box = RecordingCheckbox( width * 0.07f, height * 0.25f, 20f, 20f, text, false, this )

    }

    private fun addKeys() {

        var i = 0

        for ( ( keyBinding, id ) in map ) {

            val size = 68f;     var x = ( i - 6 ) * size * 0.2f - 1

            if ( i >= 5 ) x += 15

            val texture = Key( id, keyBinding ) {

                it.setSize(size);       it.center(width, height)

                it.onFlat();        it.addPos( x, 24f )

            }

            textures.add(texture);      i++

        }

    }

    override fun close() {

        if ( box!!.isChecked ) stop();         super.close()

    }

    override fun shouldPause(): Boolean { return false }

    private fun sequencerTick() {

        if ( !isRecording() ) return;       val sequencer = sequencer!!

        sequencer.tickPosition++

    }

    override fun handledScreenTick() { sequencerTick() }

    override fun drawForeground(matrices: MatrixStack?, mouseX: Int, mouseY: Int) {}

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        textures.forEach { it.draw(matrices) }

    }

    private fun isRecording(): Boolean { return box!!.isChecked && sequencer != null }

    private fun index( keyBinding: KeyBinding ): Int {

        var i = map.keys.indexOf(keyBinding)

        i = ( 60 + i ) + 12 * ( octave() - 4 )    // Index 60 is C4.


        item as Instrument;     return item.soundIndex( instrument, i )

    }

    private fun play(i: Int) {

        item as Instrument;     val sounds = item.stackSounds(instrument).notes

        val sound = sounds[i] ?: return


        sound.play(instrument)

        particles.spawn( player(), "simple" )

    }

    private fun write( i: Int, messageType: Int, volume: Float ) {

        if ( !isRecording() ) return


        val sequencer = sequencer!!;        val message = ShortMessage()

        val sequence = sequencer.sequence;    val tick = sequencer.tickPosition

        val track = sequence.tracks[0]


        message.setMessage( messageType, channel, i, volume.toInt() )

        track.add( MidiEvent(message, tick) )

    }

    override fun keyPressed( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {


        fun onKey(it: KeyBinding) {

            val matches = it.matchesKey(keyCode, scanCode)

            val canPlay = matches && item is Instrument


            if ( it.isPressed ) return;     if (matches) it.isPressed = true


            if ( !canPlay ) return;         val i = index(it)

            val volume = NBT.get(instrument).getFloat("Volume") * 127

            play(i);        write( i, ShortMessage.NOTE_ON, volume )

        }

        map.keys.forEach { onKey(it) }



        fun onOctaveChange( keyBinding: KeyBinding, nextOctave: Int, inRange: Boolean ) {

            val matches = keyBinding.matchesKey( keyCode, scanCode );       if (!matches) return

            if (inRange) nbt.putInt( "Octave", nextOctave )

        }

        var next = octave() + 1
        onOctaveChange( octaveUpKeyBinding!!, next, next < 8 )

        next = octave() - 1
        onOctaveChange( octaveDownKeyBinding!!, next, next > -2 )



        return super.keyPressed( keyCode, scanCode, modifiers )

    }

    override fun keyReleased( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {


        fun onKey(it: KeyBinding) {

            val matches = it.matchesKey(keyCode, scanCode)

            val canPlay = matches && item is Instrument


            if (matches) it.isPressed = false

            if ( !canPlay ) return


            val i = index(it);      item as Instrument

            val sounds = item.stackSounds(instrument).notes

            val sound = sounds[i] ?: return;        sound.fadeOut()

            write( i, ShortMessage.NOTE_OFF, 0f )

        }

        map.keys.forEach { onKey(it) }


        return super.keyReleased( keyCode, scanCode, modifiers )

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        texts.forEach { it.render(matrices) }

        octaveText.text = "${ Translations.octave }: ${ octave() }"

    }

    private fun stop() {

        val sequencer = sequencer!!;        val sequence = sequencer.sequence

        val path = directories["midis"]!!.path + "/$path"

        val file = ModFile(path)


        var message = Translations.fileWritten

        message = message.replace( "X", this.path )


        sequencer.tickPosition = 0

        try {

            MidiSystem.write(sequence, 0, file);    warnUser(message)

        } catch ( e: Exception ) {

            warnConsole("error.file_written");      e.printStackTrace()

        }

        sequence.deleteTrack( sequence.tracks[0] )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean { return false }

    companion object {

        var sequencer: Sequencer? = null

        private var sequence = Sequence( Sequence.PPQ, 10 )


        init { init() }

        private fun init() {

            if ( !MIDI.hasSystemSequencer() ) return

            sequencer = MidiSystem.getSequencer();      val sequencer = sequencer!!

            if ( !sequencer.isOpen ) sequencer.open();      sequencer.sequence = sequence

        }


        val map = mutableMapOf<KeyBinding, Identifier>()

        var octaveUpKeyBinding: KeyBinding? = null;         var octaveDownKeyBinding: KeyBinding? = null

        // @Environment(EnvType.CLIENT)
        fun registerKeyBindings() {

            val key1 = "key.$MOD_NAME";      val category = "category.$MOD_NAME.digital_console"

            val k = mutableListOf<KeyBinding>();     val textures = Textures.Keys

            for ( name in MusicTheory.octave ) {

                var key2 = name.lowercase().replace("_","_flat");   key2 = "$key1.play_$key2"

                val keyBind = KeyBinding( key2, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )

                KeyBindingHelper.registerKeyBinding(keyBind);       k.add(keyBind)

            }

            map[ k[0] ] = textures.first             // C
            map[ k[1] ] = textures.flat              // C#
            map[ k[2] ] = textures.middle            // D
            map[ k[3] ] = textures.flat              // D#
            map[ k[4] ] = textures.last              // E
            map[ k[5] ] = textures.lastFlipped       // F
            map[ k[6] ] = textures.flat              // F#
            map[ k[7] ] = textures.middle            // G
            map[ k[8] ] = textures.flat              // G#
            map[ k[9] ] = textures.middle            // A
            map[ k[10] ] = textures.flat             // A#
            map[ k[11] ] = textures.firstFlipped     // B

            var keyBind = KeyBinding( "$key1.octave_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
            octaveUpKeyBinding = KeyBindingHelper.registerKeyBinding(keyBind)

            keyBind = KeyBinding( "$key1.octave_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
            octaveDownKeyBinding = KeyBindingHelper.registerKeyBinding(keyBind)

        }

        private object Textures {

            const val PATH = "item/console/"
            val slot = textureID(PATH + "slot.png" )
            val consoleBack = textureID( PATH + "back.png" )

            object Keys {

                val first = textureID( PATH + "0.png" )
                val middle = textureID( PATH + "1.png" )
                val last = textureID( PATH + "2.png" )
                val lastFlipped = textureID( PATH + "3.png" )
                val firstFlipped = textureID( PATH + "4.png" )
                val flat = textureID( PATH + "flat.png" )

            }

        }

        private object Translations {

            val record = Translation.item("digital_console.record")
            val octave = Translation.item("gui.octave")
            val fileWritten = Translation.get("message.file_written")

        }

        private class Key(

            id: Identifier,     private val keyBinding: KeyBinding,

            private val init: (Key) -> Unit

        ) : Texture(id) {

            fun onFlat() {

                val id = id.path;           val isFlat = id.contains("flat.png")

                if ( !isFlat ) return;      setSize( w * 0.5f )

                w -= 3;     x += 18f

            }

            override fun init() { init(this) }

            override fun draw(matrices: MatrixStack) {

                if ( keyBinding.isPressed ) color( Color.GREEN )

                super.draw(matrices)

            }

        }

        private class Time( init: (RenderText) -> Unit ) : RenderText(init) {

            override fun render( matrices: MatrixStack, color: Int ) {

                val sequencer = sequencer ?: return;        if ( sequencer.tickPosition <= 0 ) return


                val tick = sequencer.tickPosition

                val minutes = tick / ( 20 * 60 );    val seconds = ( tick / 20 ) % 60

                text = ("%d:%02d").format( minutes.toInt(), seconds.toInt() )


                super.render(matrices, color)

            }

        }

        private const val MIDI_ERROR = "ERROR: Missing system MIDI sequencer!"

        class RecordingCheckbox(

            x: Float, y: Float,         w: Float, h: Float,

            message: String,            checked: Boolean,

            private val screen: DigitalConsoleScreen

        ) : Checkbox( x, y, w, h, message, checked ) {

            init {

                visible = screen.slots[0].stack.isEmpty

                screen.addDrawableChild(this)

            }

            private fun reset() {

                val sequencer = sequencer!!

                sequencer.sequence = Sequence( Sequence.PPQ, 10 )

                sequencer.sequence.createTrack()

                client().setScreen( RecordingScreen(screen) )

            }

            override fun onPress() {

                if ( sequencer == null ) { modPrint( MIDI_ERROR ); return }

                if ( isChecked ) screen.stop() else reset();      super.onPress()

            }

        }

    }

}

class PickStackScreenHandler( syncID: Int, playerInventory: PlayerInventory ) : HarmonyScreenHandler( type, syncID ) {

    private val player = playerInventory.player

    private var consoleStack = handItem( player, DigitalConsole::class )

    private var console = consoleStack.item as DigitalConsole

    init {      playerSlots( 8f, 46f, playerInventory ).forEach { addSlot(it) }      }

    private fun goBack() {

        val screen = console.createMenu(consoleStack)

        player.openHandledScreen(screen)

    }

    override fun canUse(player: PlayerEntity): Boolean { return true }

    override fun transferSlot( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val isClient = player.world.isClient;       if ( slotIndex < 0 || isClient ) return


        val slotStack = slots[slotIndex].stack;     if ( slotStack.item !is Instrument ) return


        setStack( consoleStack, slotStack );        goBack()


    }

    companion object : ModID {

        internal val factory = ScreenHandlerFactory { id, inventory, _ ->

            PickStackScreenHandler( id, inventory )

        }

        val type = ScreenHandlerType(::PickStackScreenHandler)

        fun register() {

            Registry.register( Registry.SCREEN_HANDLER, classID(), type )

            if ( !isClient() ) return

            HandledScreens.register( type, ::PickStackScreen )

        }

        private fun setStack( console: ItemStack, slotStack: ItemStack ) {

            val inventory = StackInventory( console, 1 )

            inventory.setStack( 0, slotStack );     inventory.markDirty()

        }

    }

}

// @Environment(EnvType.CLIENT)
class PickStackScreen(
    handler: PickStackScreenHandler, playerInventory: PlayerInventory, title: Text
) : HandledScreen<PickStackScreenHandler>( handler, playerInventory, title ) {

    private val texture = Texture(textureID) {

        it.setSize( 176f, 90f );       it.center(width, height)

    }

    private val pickText = RenderText {

        it.setPos(texture);     it.text = Translations.select

        it.y -= 10f

    }

    override fun init() { super.init();       texture.init();       pickText.init(textRenderer) }

    override fun shouldPause(): Boolean { return false }

    override fun drawForeground(matrices: MatrixStack?, mouseX: Int, mouseY: Int) {}

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        texture.draw(matrices);     pickText.render(matrices)

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);     super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    private companion object {

        val textureID = textureID("item/console/slots.png")

        object Translations {

            val select = Translation.item("digital_console.select")

        }

    }

}
