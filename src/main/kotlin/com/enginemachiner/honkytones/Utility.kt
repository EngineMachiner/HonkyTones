package com.enginemachiner.honkytones
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.util.math.Vec3f
import javax.sound.midi.MidiSystem

@Environment(EnvType.CLIENT)
object FFmpegImpl {

    var builder: FFmpegBuilder? = null
    var executor: FFmpegExecutor? = null
    private var ffmpegPath = clientConfig["ffmpegDir"] as String + "/"

    init {
        
        for ( value in mutableListOf("ffmpeg", "ffmpeg.exe") ) {
            ffmpegPath = getEnvPath( ffmpegPath + value, "PATH" )
                .replace(value, "")
        }

        if ( ffmpegPath == "/" ) ffmpegPath = ""

        try {
            val ffmpeg = FFmpeg( ffmpegPath + "ffmpeg" )
            val ffprobe = FFprobe( ffmpegPath + "ffprobe" )
            builder = FFmpegBuilder();      executor = FFmpegExecutor( ffmpeg, ffprobe )
        } catch ( e: Exception ) {
            printMessage( Translation.get("honkytones.error.ffmpeg") )
            printMessage( Translation.get("honkytones.message.check_console") )
            e.printStackTrace()
        }

    }

}

fun getRandomColor(): Vec3f {
    val randomList = mutableListOf<Float>()
    for ( i in 0..3 ) randomList.add( (0..255).random().toFloat() )
    return Vec3f( randomList[0], randomList[1], randomList[2] )
}

fun hasMidiSystemSequencer(): Boolean {

    try { MidiSystem.getSequencer() }
    catch ( e: Exception ) {
        printMessage( Translation.get("honkytones.error.midi-sequencer") )
        printMessage( Translation.get("honkytones.message.check_console") )
        e.printStackTrace()
        return false
    }

    return true

}

fun stringCut( s: String, lim: Int ): String {
    if (s.length > lim) { return s.substring( 0, lim ) + "..." }
    return s
}

/** Get the next value after the actual value in a collection
 * and restart after the last one */
fun <T: Any> getValueAfterValue(value: T, col: Collection<T>): T {
    var i = col.indexOf(value) + 1;   if (i > col.size - 1) { i = 0 }
    return col.elementAt(i)
}

/**
 * Verify method in the source code for newer versions or just verify in general
 */
annotation class Verify( val reason: String )
