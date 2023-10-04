package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.gui.widget.CheckboxWidget
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.InputUtil
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import org.lwjgl.glfw.GLFW
import java.awt.Color
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

private val particles = Instrument.Companion.ActionParticles

class DigitalConsoleScreenHandler(
    syncID: Int, private val playerInventory: PlayerInventory, val inventory: Inventory
) : ScreenHandler( type, syncID ) {

    constructor( syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, SimpleInventory(1) )

    constructor( stack: ItemStack, syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, StackInventory( stack, 1 ) )

    private val player = playerInventory.player;        private val world = player.world

    val stack = player.itemsHand.find { it.item is DigitalConsole }!!

    init {

        checkSize( inventory, inventory.size() );   inventory.onOpen(player)

        val slot = Slot( inventory, 0, 220, 160 );  addSlot(slot)

        checkSlot()

    }

    private fun checkSlot() {

        val current = inventory.getStack(0)

        if ( playerInventory.contains(current) || current.isEmpty || world.isClient ) return

        inventory.setStack( 0, ItemStack.EMPTY );       inventory.markDirty()

    }

    override fun close(player: PlayerEntity) {

        super.close(player);    val stack = inventory.getStack(0)

        if ( stack.isEmpty || !world.isClient ) return

        val instrument = stack.item as Instrument

        instrument.stopSounds(stack)

    }

    override fun canUse(player: PlayerEntity): Boolean { return true }

    override fun transferSlot( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val title = Translation.item("digital_console.select")

        val screenFactory = SimpleNamedScreenHandlerFactory(

            {
                syncID: Int, playerInventory: PlayerInventory, _: PlayerEntity ->

                PickStackScreenHandler( syncID, playerInventory )
            },

            Text.of("§1$title")

        )

        player.openHandledScreen(screenFactory)

    }

    companion object : ModID {

        val type = ScreenHandlerType(::DigitalConsoleScreenHandler)

        fun register() { Registry.register( Registry.SCREEN_HANDLER, classID(), type ) }

    }

}

@Environment(EnvType.CLIENT)
class DigitalConsoleScreen(
    handler: DigitalConsoleScreenHandler, playerInventory: PlayerInventory, title: Text
) : HandledScreen<DigitalConsoleScreenHandler>( handler, playerInventory, title ) {


    private val path = "item/console/"
    private val genericTexture = Identifier("textures/gui/container/generic_54.png")
    private val consoleBackTexture = textureID( path + "back.png" )
    private val firstKeyTexture = textureID( path + "0.png" )
    private val middleKeyTexture = textureID( path + "1.png" )
    private val lastKeyTexture = textureID( path + "2.png" )
    private val lastKeyFlipTexture = textureID( path + "3.png" )
    private val firstKeyFlipTexture = textureID( path + "4.png" )
    private val flatKeyTexture = textureID( path + "flat.png" )


    var recordingFileName = "";      var channel = 0

    var recordCheckbox: CheckboxWidget? = null

    var willRecord = false;         var isRecording = false


    private val consoleStack = handler.stack

    private val consoleNBT = NBT.get(consoleStack)

    private val stack = StackInventory( consoleStack, 1 ).getStack(0)

    private val item = stack.item


    init { playerInventoryTitleY -= 1000;      titleY += 12 }

    override fun init() {

        stack.holder = player();        super.init()

        val recordTitle = Translation.item("digital_console.record")

        recordCheckbox = CheckboxWidget( 25, height - 100, 20, 20, Text.of(recordTitle), false )

    }

    override fun close() {

        if ( recordCheckbox!!.isChecked ) stopRecording();  super.close()

    }

    override fun mouseClicked( mouseX: Double, mouseY: Double, button: Int ): Boolean {

        recordCheckbox!!.mouseClicked(mouseX, mouseY, button)

        return super.mouseClicked(mouseX, mouseY, button)

    }

    override fun shouldPause(): Boolean { return false }

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        RenderSystem.setShader( GameRenderer::getPositionTexShader )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, genericTexture )

        var centerX = ( width - 18 ) / 2;         var centerY = ( height - 18 ) / 2

        drawTexture( matrices, centerX + 140, centerY + 85, 7, 17, 18, 18 )

        drawTexture( matrices, centerX + 140, centerY + 79, 4, 0, 18, 6 )
        drawTexture( matrices, centerX + 140, centerY + 103, 4, 216, 18, 6 )

        drawTexture( matrices, centerX + 134, centerY + 79, 0, 0, 6, 24 )
        drawTexture( matrices, centerX + 158, centerY + 79, 170, 0, 6, 24 )

        drawTexture( matrices, centerX + 134, centerY + 103, 0, 216, 6, 18 )
        drawTexture( matrices, centerX + 158, centerY + 103, 170, 216, 6, 18 )

        centerX = ( width - 256 ) / 2;        centerY = ( height - 256 ) / 2

        RenderSystem.setShaderTexture( 0, consoleBackTexture )
        drawTexture( matrices, centerX, centerY, 0, 0, 256, 256 )

        centerX = ( width - 64 ) / 2;                   centerY = ( height - 64 ) / 2

        // C
        renderKey( firstKeyTexture, keyBindings[0], matrices, centerX - 83, centerY + 23, 64, 64 )

        val flatsCenterX = ( width - 32 ) / 2;          val flatsCenterY = ( height - 32 ) / 2

        // D_, D, E_, E, F
        renderKey( flatKeyTexture, keyBindings[1], matrices, flatsCenterX - 68, flatsCenterY + 7, 32, 32 )
        renderKey( middleKeyTexture, keyBindings[2], matrices, centerX - 55, centerY + 23, 64, 64 )
        renderKey( flatKeyTexture, keyBindings[3], matrices, flatsCenterX - 41, flatsCenterY + 7, 32, 32 )
        renderKey( lastKeyTexture, keyBindings[4], matrices, centerX - 27, centerY + 23, 64, 64 )
        renderKey( lastKeyFlipTexture, keyBindings[5], matrices, centerX + 1, centerY + 23, 64, 64 )

        // G_, A_, B_
        for ( i in 0..2 ) renderKey( flatKeyTexture, keyBindings[ 6 + i * 2 ], matrices, flatsCenterX + 15 + 28 * i, flatsCenterY + 7, 32, 32 )

        // G, A, B
        renderKey( middleKeyTexture, keyBindings[7], matrices, centerX + 29, centerY + 23, 64, 64 )
        renderKey( middleKeyTexture, keyBindings[9], matrices, centerX + 57, centerY + 23, 64, 64 )
        renderKey( firstKeyFlipTexture, keyBindings[11], matrices, centerX + 85, centerY + 23, 64, 64 )

    }

    override fun keyPressed( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {

        keyBindings.forEach {

            val matches = it.matchesKey( keyCode, scanCode )

            val canPlay = matches && item is Instrument

            if ( it.isPressed ) return@forEach;     if (matches) it.isPressed = true

            if ( !canPlay ) return@forEach;         val i = getIndex(it)

            val volume = 127 * NBT.get(stack).getFloat("Volume")

            play(i);        record( i, ShortMessage.NOTE_ON, volume )

        }

        val octave = consoleNBT.getInt("Octave")

        if ( octaveUpKeyBinding!!.matchesKey( keyCode, scanCode ) ) {

            val octave = octave + 1

            if ( octave < 8 ) consoleNBT.putInt( "Octave", octave )

        }

        if ( octaveDownKeyBinding!!.matchesKey( keyCode, scanCode ) ) {

            val octave = octave - 1

            if ( octave > - 2 ) consoleNBT.putInt( "Octave", octave )

        }

        return super.keyPressed(keyCode, scanCode, modifiers)

    }

    private fun getIndex(keyBinding: KeyBinding): Int {

        val octave = consoleNBT.getInt("Octave");      item as Instrument

        // 60 = C4.
        var i = keyBindings.indexOf(keyBinding);            i = ( 60 + i ) + 12 * ( octave - 4 )

        return item.soundIndex( stack, i )

    }

    private fun play(index: Int) {

        item as Instrument;     val sounds = item.stackSounds(stack).notes

        val sound = sounds[index] ?: return

        if ( sound.isPlaying() ) sound.addTimesStopped();       sound.play(stack)

        particles.clientSpawn( player()!!, "simple" )

    }

    private fun record( index: Int, messageType: Int, volume: Float ) {

        if ( !canRecord() ) return;         val message = ShortMessage()

        message.setMessage( messageType, channel, index, volume.toInt() )

        val sequence = sequencer!!.sequence;    val tick = sequencer.tickPosition

        sequence.tracks[0].add( MidiEvent( message, tick ) )

    }

    private fun canRecord(): Boolean { return isRecording && sequencer != null }

    override fun keyReleased( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {

        keyBindings.forEach {

            val matches = it.matchesKey( keyCode, scanCode )

            val canPlay = matches && item is Instrument

            if (matches) it.isPressed = false

            if ( !canPlay ) return@forEach; item as Instrument

            val i = getIndex(it);   val sounds = item.stackSounds(stack).notes

            val sound = sounds[i] ?: return@forEach

            sound.fadeOut();        record( i, ShortMessage.NOTE_OFF, 0f )

        }

        return super.keyReleased( keyCode, scanCode, modifiers )

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        children().forEach { it as Drawable;     it.render( matrices, mouseX, mouseY, delta ) }

        var octaveTitle = Translation.item("gui.octave")
        octaveTitle = "$octaveTitle: " + consoleNBT.getInt("Octave")
        
        textRenderer.draw( matrices, octaveTitle, width * 0.5f + 10, height * 0.5f - 65, Color.WHITE.rgb )

        drawTime(matrices);       recordCheckbox!!.renderButton( matrices, mouseX, mouseY, delta )

    }

    override fun handledScreenTick() { recordingMenu();        recordTick() }

    private fun recordingMenu() {

        val recordCheckbox = recordCheckbox!!

        if ( !recordCheckbox.isChecked || willRecord ) return

        if ( sequencer == null ) { recordCheckbox.onPress(); return }

        sequencer.sequence = Sequence( Sequence.PPQ, 10 )

        sequencer.sequence.createTrack()

        client!!.setScreen( RecordingScreen(this) )

        willRecord = true

    }

    private fun recordTick() {

        if ( !canRecord() ) return;     sequencer!!.tickPosition++

        if ( !recordCheckbox!!.isChecked ) stopRecording()

    }

    private fun drawTime(matrices: MatrixStack) {

        if ( !canRecord() ) return;         val tick = sequencer!!.tickPosition

        val minutes = tick / ( 20 * 60 );    val seconds = ( tick / 20 ) % 60

        val format = ("%d:%02d").format( minutes.toInt(), seconds.toInt() )

        textRenderer.draw( matrices, format, width * 0.5f + 3, height * 0.5f - 49, Color.WHITE.rgb )

    }

    private fun stopRecording() {

        val sequence = sequencer!!.sequence

        isRecording = false;    willRecord = false;     sequencer.tickPosition = 0

        try {

            val file = ModFile( directories["midis"]!!.path + "/$recordingFileName" )

            MidiSystem.write( sequence, 0, file )

            val message = Translation.get("message.file_written")
                .replace( "X", this.recordingFileName )

            warnUser(message)

        } catch ( e: Exception ) {

            warnUser( Translation.get("error.file_written") )
            warnUser( Translation.get("message.check_console") )

            e.printStackTrace()

        }

        sequence.deleteTrack( sequence.tracks[0] );     sequence.createTrack()

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean { return false }

    private fun renderKey(
        textureID: Identifier, keyBinding: KeyBinding,
        matrices: MatrixStack, x: Int, y: Int, w: Int, h: Int
    ) {

        RenderSystem.setShaderTexture( 0, textureID )

        if ( keyBinding.isPressed ) RenderSystem.setShaderColor( 0.25f, 1f, 0.25f, 1f )

        drawTexture( matrices, x, y, 0f, 0f, w, h, w, h )

        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )

    }

    companion object {

        val sequencer = RecordingScreen.sequencer;          val keyBindings = mutableListOf<KeyBinding>()

        var octaveUpKeyBinding: KeyBinding? = null;         var octaveDownKeyBinding: KeyBinding? = null

        @Environment(EnvType.CLIENT)
        fun registerKeyBindings() {

            val key1 = "key.$MOD_NAME";      val category = "category.$MOD_NAME.digital_console"

            for ( name in MusicTheory.octave ) {

                var key2 = name.lowercase().replace("_","_flat");   key2 = "$key1.play_$key2"

                val keybind = KeyBinding( key2, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )

                keyBindings.add( KeyBindingHelper.registerKeyBinding(keybind) )

            }

            var keybind = KeyBinding( "$key1.octave_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
            octaveUpKeyBinding = KeyBindingHelper.registerKeyBinding(keybind)

            keybind = KeyBinding( "$key1.octave_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
            octaveDownKeyBinding = KeyBindingHelper.registerKeyBinding(keybind)

        }

        fun register() {

            HandledScreens.register( DigitalConsoleScreenHandler.type, ::DigitalConsoleScreen )

            PickStackScreen.register()

        }

    }

}

class PickStackScreenHandler( syncID: Int, playerInventory: PlayerInventory ) : ScreenHandler( type, syncID ) {

    private val player = playerInventory.player

    private var consoleStack = player.itemsHand.find { it.item is DigitalConsole }!!

    private var console = consoleStack.item as DigitalConsole

    init {

        val w = 18;     val x = 8;      val y = w * 6 - 21

        for ( i in 0 .. 2 ) { for ( j in 0 .. 8 ) {

            val index = j + i * 9 + 9;      val x = w * j + x

            val y = w * ( i + 6 ) - y + 13

            addSlot( Slot( playerInventory, index, x, y ) )

        } }

        for ( j in 0 .. 8 ) {

            val x = w * j + x;      val y = w * 10 - y - 1

            addSlot( Slot( playerInventory, j, x, y ) )

        }

    }

    override fun canUse(player: PlayerEntity): Boolean { return true }

    override fun transferSlot( player: PlayerEntity, index: Int ): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        if ( slotIndex < 0 || player.world.isClient ) return

        val slotStack = slots[slotIndex].stack;     if ( slotStack.item !is Instrument ) return

        val id = netID("client_sync");      val netSlot = player.inventory.getSlotWithStack(slotStack)
        val buf = PacketByteBufs.create();      buf.writeInt(netSlot)

        ServerPlayNetworking.send( player as ServerPlayerEntity, id, buf )

        setStack( consoleStack, slotStack )

        val screen = console.createMenu(consoleStack);     player.openHandledScreen(screen)

    }

    companion object : ModID {

        val type = ScreenHandlerType(::PickStackScreenHandler)

        fun register() { Registry.register( Registry.SCREEN_HANDLER, classID(), type ) }

        private fun setStack( console: ItemStack, slotStack: ItemStack ) {

            val inventory = StackInventory( console, 1 )

            inventory.setStack( 0, slotStack );     inventory.markDirty()

        }

        fun networking() {

            if ( !isClient() ) return

            val id = netID("client_sync")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler, buf: PacketByteBuf,
                _: PacketSender ->

                val slot = buf.readInt()

                client.send {

                    val console = player()!!.itemsHand.find { it.item is DigitalConsole }!!

                    val stack = player()!!.inventory.getStack(slot)

                    setStack( console, stack )

                }

            }

        }

    }

}

@Environment(EnvType.CLIENT)
class PickStackScreen(
    handler: PickStackScreenHandler, playerInventory: PlayerInventory, title: Text
) : HandledScreen<PickStackScreenHandler>( handler, playerInventory, title ) {

    private val genericTexture = Identifier("textures/gui/container/generic_54.png")

    init { titleY += 17;    playerInventoryTitleY -= 1000;      backgroundHeight -= 20 }

    override fun shouldPause(): Boolean { return false }

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        RenderSystem.setShader( GameRenderer::getPositionTexShader )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, genericTexture )

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val w = backgroundWidth;        val h = 6 * 18 + 17

        drawTexture( matrices, centerX, centerY + 20, 0, 126, w, h )
        drawTexture( matrices, centerX, centerY + 18, 0, 0, w, 3 )

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);     super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    companion object {

        fun register() { HandledScreens.register( PickStackScreenHandler.type, ::PickStackScreen ) }

    }

}
