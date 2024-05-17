package com.enginemachiner.honkytones

import MediaInfo
import com.enginemachiner.harmony.envPath
import com.enginemachiner.harmony.output
import com.enginemachiner.harmony.warnConsole
import com.enginemachiner.harmony.warnUser
import com.enginemachiner.honkytones.Init.Companion.directories
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import kotlin.io.path.pathString

// @Environment(EnvType.CLIENT)
fun deleteDownloads() {

    val directory = directories["streams"]!!;       val files = directory.listFiles()!!

    for ( file in files ) file.delete()

}

// @Environment(EnvType.CLIENT)
private interface ExternalProcessing {

    fun read(options: String): String? {

        val list = options.split(" ")

        val process = ProcessBuilder(list).start()

        val output = output( process.inputReader() )

        process.destroyForcibly();      return output

    }

}

// @Environment(EnvType.CLIENT)
class FFmpeg( input: String, output: String ) : ExternalProcessing {

    private val extraOptions = "-c:a libvorbis -b:a 52k"

    private var options = "$path -i $input -f ogg $extraOptions $output"

    init {

        try { read(options) } catch (e: Exception) { missingError(e) }

    }

    companion object {

        /** This path is used for the ffmpeg process. */
        private var path = ""

        /** This path is set as the ffmpeg location in the yt-dlp parameter. */
        private var cmdPath = path

        init { refresh() };    fun path(): String { return cmdPath }

        fun refresh() {

            path = Config.client().ffmpegDirectory;       setPaths()

        }

        private fun setPaths() {

            val hint = "ffmpeg.exe"

            if ( !path.endsWith(hint) ) path += hint

            if ( !cmdPath.endsWith(hint) ) cmdPath += hint

            path = envPath(path);       cmdPath = envPath(cmdPath)

            if ( cmdPath.isNotEmpty() && cmdPath[0] == '$' ) return

            val gameDir = FabricLoader.getInstance().gameDir.pathString

            cmdPath = gameDir + "\\" + cmdPath

        }

        fun missingError(e: Exception) {

            warnConsole("error.ffmpeg");     e.printStackTrace()

        }

    }

}

// @Environment(EnvType.CLIENT)
private val mapper = ObjectMapper()

/** Handles yt-dlp requests and uses ffmpeg internally. */
// @Environment(EnvType.CLIENT)
open class YTDLP(input: String) : ExternalProcessing {

    private val fileName = "%(id)s - %(title)s.%(ext)s"

    private val output = directory + fileName

    private var formerOptions = "$path \"$input\" -o \"$output\" --no-playlist --no-mark-watched"

    private var options = formerOptions;    val info = info()

    private fun info(): MediaInfo? {

        val options = formerOptions + " " + dataOptions.joinToString(" ")

        val output = exec(options) ?: return null

        return try { mapper.readValue(output) }
        catch (e: Exception) { dataFetchError(e); null }

    }

    private fun reset() { options = formerOptions }

    fun add(option: String) { options += " $option" }

    private fun exec(options: String): String? {

        return try { read(options) }
        catch (e: Exception) { mediaError(e); null }

    }

    private fun exec(): String? { return exec(options) }

    fun requestAudio(): Boolean {

        val ffmpegPath = FFmpeg.path()

        var params = "--extract-audio --audio-format vorbis --audio-quality 52k "

        params += "--ffmpeg-location $ffmpegPath"

        if ( isMissingFFmpeg() ) return false

        reset();    add(params);      exec() ?: return false

        return true

    }

    fun requestVideo(): Boolean {

        reset();    add( "--format 18" );     exec() ?: return false

        return true

    }

    fun output(ext: String): String? {

        val info = info ?: return null

        return output.replace( "%(id)s", info.id )
            .replace( "%(title)s", info.title )
            .replace( "%(ext)s", ext )

    }

    companion object {

        private var path = ""

        private val directory = directories["streams"]!!.path + "\\"

        private val dataOptions = listOf(

            "--wait-for-video", "5",

            "--dump-json",    "--no-colors",    "--skip-download",

            "--youtube-skip-dash-manifest",

            "--no-download-archive"

        )

        init { refresh() }

        fun exists(): Boolean { return File(path).exists() }

        fun refresh() {

            path = Config.client().ytdlpPath;       path = envPath(path)

        }

        private fun dataFetchError(e: Exception) {

            warnConsole("error.parse");     e.printStackTrace()

        }

        private fun mediaError(e: Exception) {

            warnConsole("error.missing_ytdlp");     e.printStackTrace()

        }

        private fun isMissingFFmpeg(): Boolean {

            var path = FFmpeg.path();      val hint = "ffmpeg.exe"

            if ( !path.endsWith(hint) ) path += hint

            if ( !File(path).exists() ) {

                warnUser("error.ffmpeg");    return true

            }

            return false

        }

    }

}