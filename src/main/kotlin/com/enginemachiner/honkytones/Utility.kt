package com.enginemachiner.honkytones
import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Language
import net.minecraft.util.math.Vec3f
import org.apache.commons.validator.routines.UrlValidator
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import kotlin.math.PI
import kotlin.reflect.KClass

object Utility {

    @JvmField   // To be used while testing mixins.
    val print: (String) -> Unit = ::modPrint

}

/** Verify logic or methods if they are based on vanilla behaviour, etc. */
annotation class Verify( val reason: String )

private val logger: Logger = LogManager.getLogger("HonkyTones")

fun modPrint( any: Any? ) { logger.info("$any") }

@Environment(EnvType.CLIENT)
fun client(): MinecraftClient { return MinecraftClient.getInstance()!! }

fun isClient(): Boolean { return FabricLoaderImpl.INSTANCE.environmentType == EnvType.CLIENT }

fun randomColor(): Vec3f {

    val r = ( 0..255 ).random();    val g = ( 0..255 ).random()
    val b = ( 0..255 ).random()

    return Vec3f( r.toFloat(), g.toFloat(), b.toFloat() )

}

/** Shortens string and adds "..." by a number of chars. */
fun shorten( s: String, limit: Int ): String {
    
    if ( s.length > limit ) { return s.substring( 0, limit ) + "..." }
    
    return s
    
}

/** Get the next value in a collection or repeat at first value.  */
fun <T: Any> cycle( value: T, col: Collection<T> ): T {

    var i = col.indexOf(value) + 1;   if ( i > col.size - 1 ) i = 0

    return col.elementAt(i)

}

fun isValidUrl( url: String ): Boolean {

    val b = !url.startsWith("http://") && !url.startsWith("https://")

    var url = url;          if (b) url = "http://$url"

    return UrlValidator().isValid(url)

}

object Translation {

    private val language: Language = Language.getInstance()

    fun has( key: String ): Boolean { return language.hasTranslation("$MOD_NAME.$key") }
    fun get( key: String ): String { return Text.translatable( "$MOD_NAME.$key" ).string }
    fun item( key: String ): String { return Text.translatable( "item.$MOD_NAME.$key" ).string }
    fun block( key: String ): String { return Text.translatable( "block.$MOD_NAME.$key" ).string }

}

interface ModID {

    fun classID(): Identifier { return modID( className() ) }

    fun className(): String { return Companion.className( this::class ) }

    fun netID(id: String): Identifier {

        val className = className();    return Identifier("$className:$id")

    }

    companion object {

        fun className( kclass: KClass<*>): String {

            var name = kclass.simpleName!!

            if ( kclass.isCompanion ) {

                name = Regex("[A-Z].+").find( kclass.qualifiedName!! )!!.value

            }

            name = name.replace( "ScreenHandler", "" )
                .replace( ".Companion", "" )
                .replace( Regex("([A-Z])"), "_$1" )
                .lowercase().substring(1)

            return name

        }

    }

}


fun degreeToRadians( angle: Double ): Double {

    return angle % 360 * 2 * PI / 360

}

fun modID(id: String): Identifier { return Identifier( MOD_NAME, id ) }

fun textureID(id: String): Identifier { return Identifier( MOD_NAME, "textures/$id" ) }

class Timer( private val tickLimit: Int,     private val function: () -> Unit ) {

    private var ticks = 0;      init { timers.add(this) }

    private fun kill() { timers.remove(this) }

    fun tick() { if ( ticks > tickLimit ) { function(); kill() } else ticks++ }

    companion object {

        val timers = mutableListOf<Timer>()

        fun tickTimers() { timers.toSet().forEach { it.tick() } }

    }

}