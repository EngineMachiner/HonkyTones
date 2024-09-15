package com.enginemachiner.honkytones.client

import MediaInfo
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.sendMessage
import com.enginemachiner.harmony.envPath
import com.enginemachiner.harmony.output
import com.enginemachiner.honkytones.client.HonkyTones.Companion.directories
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.fabricmc.loader.api.FabricLoader
import org.apache.commons.validator.routines.UrlValidator
import java.io.File
import kotlin.io.path.pathString

fun isValidUrl(url: String): Boolean {

    val b = !url.startsWith("http://") && !url.startsWith("https://")

    var url = url;          if (b) url = "http://$url"

    return UrlValidator().isValid(url)

}

fun deleteDownloads() {

    val directory = directories["streams"]!!;       val files = directory.listFiles()!!

    for ( file in files ) file.delete()

}

private interface ExternalProcessing {

    fun read(options: String): String? {

        val list = options.split(" ")

        val process = ProcessBuilder(list).start()

        val output = output( process.inputReader() )

        process.destroyForcibly();      return output

    }

}

class FFmpeg( input: String, output: String ) : ExternalProcessing {

    private val extraOptions = "-c:a libvorbis -b:a 52k"

    private var options = "$path -i $input -f ogg $extraOptions $output"

    init {

        try { read(options) } catch (exception: Exception) { missingError(exception) }

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

        fun missingError(exception: Exception) {

            Message( "error.ffmpeg", exception ).console()

        }

    }

}

private val mapper = ObjectMapper()

/** Handles yt-dlp requests and uses ffmpeg internally. */
open class YTDLP( input: String ) : ExternalProcessing {

    private val fileName = "%(id)s - %(title)s.%(ext)s"

    private val output = directory + fileName

    private var formerOptions = "$path \"$input\" -o \"$output\" --no-playlist --no-mark-watched --replace-in-metadata \"title\" \"[\\/]\" \"\""

    private var options = formerOptions;    val info = info()


    private fun info(): MediaInfo? {

        val options = formerOptions + " " + dataOptions.joinToString(" ")

        val output = exec(options) ?: return null

        return try { mapper.readValue(output) } catch (exception: Exception) { dataFetchError(exception); null }

    }

    private fun reset() { options = formerOptions }

    fun add(option: String) { options += " $option" }

    private fun exec(options: String): String? {

        return try { read(options) } catch (exception: Exception) { mediaError(exception); null }

    }

    private fun exec(): String? { return exec(options) }

    fun requestAudio(): Boolean {

        val ffmpegPath = FFmpeg.path()

        var params = "--extract-audio --audio-format vorbis --audio-quality 64k "

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

        private fun dataFetchError(exception: Exception) {

            Message( "error.parse", exception ).console()

        }

        private fun mediaError(exception: Exception) {

            Message( "error.missing_ytdlp", exception ).console()

        }

        private fun isMissingFFmpeg(): Boolean {

            var path = FFmpeg.path();      val hint = "ffmpeg.exe"

            if ( !path.endsWith(hint) ) path += hint

            if ( !File(path).exists() ) {

                sendMessage("error.ffmpeg");    return true

            }

            return false

        }

    }

}