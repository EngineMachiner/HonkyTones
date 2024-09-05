package com.enginemachiner.honkytones.client.items.console

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.honkytones.MusicTheory
import com.enginemachiner.honkytones.client.HonkyTones.Companion.directories
import com.enginemachiner.honkytones.client.MIDI
import com.enginemachiner.honkytones.client.items.console.DigitalConsoleScreen.Companion.KeyBindings.octaveDown
import com.enginemachiner.honkytones.client.items.console.DigitalConsoleScreen.Companion.KeyBindings.octaveUp
import com.enginemachiner.honkytones.client.items.instruments.Instrument
import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler.Companion.console
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler.Companion.inventory
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler.Companion.type
import com.enginemachiner.honkytones.items.console.PickStackScreenHandler
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.awt.Color
import javax.sound.midi.*

private val particles = Instrument.ActionParticles

class DigitalConsoleScreen(

    handler: DigitalConsoleScreenHandler,       playerInventory: PlayerInventory,       title: Text

) : HandledScreen<DigitalConsoleScreenHandler>( handler, playerInventory, title ) {

    private val console = console( player() );          private val nbt = nbt(console)

    private val instrument = inventory(console).getStack(0)

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


        val nbt = nbt(console);       nbt.putBoolean( "damageStack", true )

        send(nbt)

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

        for ( ( keyBinding, id ) in KeyBindings.map ) {

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

    override fun drawForeground( matrices: MatrixStack?, mouseX: Int, mouseY: Int ) {}

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        textures.forEach { it.draw(matrices) }

    }

    private fun isRecording(): Boolean { return box!!.isChecked && sequencer != null }

    private fun sounds(): Instrument.Sounds.Copy { return soundsCopy(instrument) }

    private fun index( keyBinding: KeyBinding ): Int {

        val map = KeyBindings.map;          var i = map.keys.indexOf(keyBinding)

        i = ( 60 + i ) + 12 * ( octave() - 4 )    // Index 60 is C4.


        return sounds().pos(i)

    }

    private fun play(i: Int) {

        val sounds = sounds().common()

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


        fun onKey( it: KeyBinding ) {

            val matches = it.matchesKey(keyCode, scanCode)

            val canPlay = matches && item is InstrumentItem


            if ( it.isPressed ) return;     if (matches) it.isPressed = true


            if ( !canPlay ) return;         val i = index(it)

            val volume = nbt(instrument).getFloat("Volume") * 127

            play(i);        write( i, ShortMessage.NOTE_ON, volume )

        }

        val map = KeyBindings.map;          map.keys.forEach { onKey(it) }



        fun onOctaveChange( keyBinding: KeyBinding, nextOctave: Int, inRange: Boolean ) {

            val matches = keyBinding.matchesKey( keyCode, scanCode );       if (!matches) return

            if (inRange) nbt.putInt( "Octave", nextOctave )

        }

        var next = octave() + 1
        onOctaveChange( octaveUp.bind(), next, next < 8 )

        next = octave() - 1
        onOctaveChange( octaveDown.bind(), next, next > -2 )


        return super.keyPressed( keyCode, scanCode, modifiers )

    }

    override fun keyReleased( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {


        fun onKey(it: KeyBinding) {

            val matches = it.matchesKey(keyCode, scanCode)

            val canPlay = matches && item is InstrumentItem


            if (matches) it.isPressed = false

            if ( !canPlay ) return


            val i = index(it)

            val sounds = sounds().common()

            val sound = sounds[i] ?: return;        sound.fadeOut()

            write( i, ShortMessage.NOTE_OFF, 0f )

        }

        val map = KeyBindings.map;          map.keys.forEach { onKey(it) }


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

            MidiSystem.write(sequence, 0, file);    sendMessage(message)

        } catch (exception: Exception) {

            Message( "error.file_written", exception ).console()

        }

        sequence.deleteTrack( sequence.tracks[0] )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean { return false }

    companion object : ModID {

        var sequencer: Sequencer? = null

        private var sequence = Sequence( Sequence.PPQ, 10 )

        override fun className(): String { return DigitalConsoleScreenHandler.className() }

        init { init() }

        private fun init() {

            if ( !MIDI.hasSystemSequencer() ) return

            sequencer = MidiSystem.getSequencer();      val sequencer = sequencer!!

            if ( !sequencer.isOpen ) sequencer.open();      sequencer.sequence = sequence

        }

        fun register() {

            HandledScreens.register( type, ::DigitalConsoleScreen )

            PickStackScreen.register()

        }

        fun networking() {

            val netID = netID("sync")

            Receiver(netID).register {

                val slot = it.readInt()

                client().send {

                    val player = player()

                    val selected = player.inventory.getStack(slot)

                    PickStackScreenHandler.set( player, selected )

                }

            }

        }

        object KeyBindings {

            private const val CATEGORY = "digital_console"

            val octaveUp = ModKey( "octave_up", CATEGORY )
            val octaveDown = ModKey( "octave_down", CATEGORY )

            val map = mutableMapOf<KeyBinding, Identifier>()

            fun register() {

                octaveUp.register();        octaveDown.register()


                val keyBindings = mutableListOf<KeyBinding>();          val textures = Textures.Keys

                for ( name in MusicTheory.octave ) {

                    val note = name.lowercase().replace("_","_flat")

                    val modKey = ModKey( "play_$note", "digital_console" )

                    modKey.register();         keyBindings.add( modKey.bind() )

                }


                map[ keyBindings[0] ] = textures.first             // C
                map[ keyBindings[1] ] = textures.flat              // C#
                map[ keyBindings[2] ] = textures.middle            // D
                map[ keyBindings[3] ] = textures.flat              // D#
                map[ keyBindings[4] ] = textures.last              // E
                map[ keyBindings[5] ] = textures.lastFlipped       // F
                map[ keyBindings[6] ] = textures.flat              // F#
                map[ keyBindings[7] ] = textures.middle            // G
                map[ keyBindings[8] ] = textures.flat              // G#
                map[ keyBindings[9] ] = textures.middle            // A
                map[ keyBindings[10] ] = textures.flat             // A#
                map[ keyBindings[11] ] = textures.firstFlipped     // B

            }

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

            id: Identifier, private val keyBinding: KeyBinding,

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

                val stack = screen.instrument;          visible = !stack.isEmpty

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

    companion object {

        fun register() {

            HandledScreens.register( PickStackScreenHandler.type, ::PickStackScreen )

        }

        private val textureID = textureID("item/console/slots.png")

        private object Translations {

            val select = Translation.item("digital_console.select")

        }

    }

}