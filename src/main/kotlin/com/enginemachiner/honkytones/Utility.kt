package com.enginemachiner.honkytones
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerCompanion
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.particle.Particle
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.AirBlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3f
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import javax.sound.midi.MidiSystem
import kotlin.reflect.KClass

object FFmpegImpl {

    var builder: FFmpegBuilder? = null
    var executor: FFmpegExecutor? = null

    init {

        try {
            builder = FFmpegBuilder()
            executor = FFmpegExecutor( FFmpeg("ffmpeg"), FFprobe("ffprobe") )
        } catch ( e: Exception ) {
            printMessage("ffmpeg executables / files are missing or incompatible!")
        }

    }

}

fun getRandomColor(): Vec3f {
    val randomList = mutableListOf<Float>()
    for ( i in 0..3 ) randomList.add( (0..255).random().toFloat() )
    return Vec3f( randomList[0], randomList[1], randomList[2] )
}

/** This is on the client side */
fun findByUUID(client: MinecraftClient, uuid: String): Entity? {

    var entity: Entity? = null
    val list = mutableListOf( client.world!!.entities, MusicPlayerCompanion.entities )
    for ( entities in list ) {
        entity = entities.find { it.uuidAsString == uuid }
        if (entity != null) break
    }

    return entity

}

/** Puts the message in the chat, actionBar = false */
fun printMessage( s: String ) { printMessage( s, false ) }

/** Puts the message in the chat, actionBar = false */
fun printMessage( player: PlayerEntity, s: String ) { printMessage( player, s, false ) }

/** Puts the message in the chat */
fun printMessage( s: String, b: Boolean ) {
    val player = MinecraftClient.getInstance().player ?: return
    printMessage( player, s, b )
}

/** Puts the message in the chat */
fun printMessage( player: PlayerEntity, s: String, b: Boolean ) {
    player.sendMessage( Text.of( "§3" + Base.DEBUG_NAME + " §f" + s ), b )
}

const val menuMsg = "You can't open the menu while " +
        "holding two %item% at the same time!"

val hands = arrayOf( Hand.MAIN_HAND, Hand.OFF_HAND )

val equipment = arrayOf( EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND )

fun sendStatus( it: LivingEntity?, stack: ItemStack ) {
    val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
    val index = nbt.getInt("hand")
    it!!.sendEquipmentBreakStatus( equipment[index] )
}

fun createStackIfMissing( stacks: MutableList<ItemStack>, name: String, index: Int ) {
    val stack = stacks.elementAtOrNull(index)
    if ( stack == null || stack.item is AirBlockItem ) {
        while ( stacks.size < index ) stacks.add( ItemStack( Items.AIR ) )
        val id = Identifier( Base.MOD_NAME, name )
        val item = Registry.ITEM.get( id )
        stacks.add( index, ItemStack( item ) )
    }
}

abstract class SpecificItemToSlotScreenHandler( type: ScreenHandlerType<*>,
                                               syncID: Int ) : ScreenHandler( type, syncID ) {

    fun isAllowed(slotItem: Item, cursorItem: Item,
                  classA: KClass<*>, range: Boolean ): Boolean {

        var b = classA.isInstance(slotItem) || classA.isInstance(cursorItem)
        b = b && range

        return b

    }

}

fun isModItem( item: Item ): Boolean {
    return Registry.ITEM.getId(item).namespace == Base.MOD_NAME
}

// Entities that can be muted
interface CanBeMuted {

    fun shouldBlacklist( player: PlayerEntity, entity: Entity, vec: Vec3d ): Boolean {

        val b = shouldBlacklist( player, entity, entity::class )

        var particle = blacklist[entity]
        if (!b || particle == null) return b
        particle = particle as MuteParticle
        particle.followPosOffset = particle.followPosOffset
            .add(Vec3d(0.5, -1.0, 0.5))
        return b

    }

    fun shouldBlacklist( player: PlayerEntity, entity: Entity,
                         kClass: KClass<out Entity> ): Boolean {

        val b = player.isInSneakingPose
        if ( b && kClass.isInstance(entity) ) {

            if (player.world.isClient) {
                if ( !blacklist.keys.contains(entity) ) {
                    blacklist[entity] = spawnMuteNote(entity)
                } else {
                    blacklist[entity]!!.markDead()
                    blacklist.remove(entity)
                }
            }

            return true

        }

        return false

    }

    private fun spawnMuteNote(entity: Entity): Particle {

        val client = MinecraftClient.getInstance()
        val manager = client.particleManager

        val particle = manager.addParticle(
            Particles.MUTE, entity.x, entity.y, entity.z,
            0.0, 0.0, 0.0
        ) as MuteParticle
        particle.entity = entity

        return particle

    }

    companion object {
        val blacklist = mutableMapOf<Entity, Particle>()
    }

}

class ChannelTextFieldWidget( textRenderer: TextRenderer, x: Int, y: Int, w: Int, h: Int )
: TextFieldWidget( textRenderer, x, y, w, h, Text.of("") ) {

    init { setMaxLength(2) }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        // Channel input restrictions
        val case1 = text.isBlank() && !isFocused
        val case2 = text.length == 1
                && text.contains(Regex("[^1-9]"))

        val b: Boolean
        var case3 = text.length == 2
        if ( case3 ) {
            b = text[1].toString().contains( Regex("[^0-6]") )
            case3 = text[0] != '1' || b
        }

        if ( case1 || case2 || case3 ) text = "1"

        super.render(matrices, mouseX, mouseY, delta)

    }

}

fun trackPlayerOnNbt( nbt: NbtCompound, entity: Entity, world: World ) {

    val tempPlayer = world.players.find { it.uuidAsString == nbt.getString("PlayerUUID") }
    if ( tempPlayer != entity ) nbt.putString("PlayerUUID", entity.uuidAsString)

}

fun trackHandOnNbt( stack: ItemStack, entity: Entity ) {

    val nbt = stack.nbt!!.getCompound( Base.MOD_NAME )

    val index = entity.itemsHand.indexOf(stack)
    val b = !nbt.contains("hand") || nbt.getInt("hand") != index
    if ( entity is PlayerEntity && index >= 0 && b ) nbt.putInt( "hand", index )

}

fun writeDisplayNameOnNbt( stack: ItemStack, toNbt: NbtCompound ) {
    val formerNbt = stack.nbt!!
    if ( !toNbt.contains("removeName") ) {
        val displayNbt = formerNbt.getCompound("display")
        toNbt.put("display",  displayNbt)
    }
}

fun hasMidiSystemSequencer(): Boolean {

    try { MidiSystem.getSequencer() }
    catch ( e: Exception ) {
        printMessage("Couldn't get the OS default midi sequencer!")
        e.printStackTrace()
        return false
    }

    return true

}

/**
 * Verify method in the source code for newer versions
 */
annotation class Verify