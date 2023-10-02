package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.directories
import com.fasterxml.jackson.databind.ObjectMapper
import com.sapher.youtubedl.YoutubeDLRequest
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import youtubedl.VideoInfo
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

@Environment(EnvType.CLIENT)
object FFmpegImpl {

    var builder: FFmpegBuilder? = null;     var executor: FFmpegExecutor? = null

    private var path = clientConfig["ffmpeg_directory"] as String + "/"

    init {

        for ( value in mutableListOf( "ffmpeg", "ffmpeg.exe" ) ) {

            path = envPath( path + value, "PATH" )
                .replace( value, "" )

        }

        if ( path == "/" ) path = ""

        try {

            val ffmpeg = FFmpeg( path + "ffmpeg" )
            val ffprobe = FFprobe( path + "ffprobe" )

            builder = FFmpegBuilder();      executor = FFmpegExecutor( ffmpeg, ffprobe )

        } catch ( e: Exception ) {

            warnUser( Translation.get("error.ffmpeg") )
            warnUser( Translation.get("message.check_console") )

            e.printStackTrace()

        }

    }

}

@Environment(EnvType.CLIENT)
class MediaRequest(input: String) : YTDLRequest(input) {
    init { setOption("no-playlist");   setOption("no-mark-watched") }
}

@Environment(EnvType.CLIENT)
open class YTDLRequest(input: String) : YoutubeDLRequest(input) {
    public override fun buildOptions(): String { return super.buildOptions() }
}

@Environment(EnvType.CLIENT)
private val mapper = ObjectMapper()

@Environment(EnvType.CLIENT)
fun infoRequest(path: String): VideoInfo? {

    val request = YTDLRequest(path)
    request.setOption("youtube-skip-dash-manifest")
    request.setOption("dump-json")
    request.setOption("no-playlist")
    request.setOption("no-mark-watched")
    request.setOption("no-colors")
    request.setOption("no-download-archive")
    request.setOption( "wait-for-video", 5 )
    request.setOption("skip-download")

    val response = executeYTDL(request);    if ( response.isEmpty() ) return null

    return try { mapper.readValue( response, VideoInfo::class.java ) } catch (e: Exception) {

        warnUser( Translation.get("error.parse") )
        warnUser( Translation.get("message.check_console") )

        e.printStackTrace();    null

    }

}

@Environment(EnvType.CLIENT)
fun executeYTDL(request: YTDLRequest): String {

    var path = clientConfig["youtube-dl_path"] as String
    path = envPath( path, "PATH" )

    var command = listOf( "\"$path\"" )
    command = command + request.buildOptions().split(" ")

    val processBuilder = ProcessBuilder(command)

    val directory = request.directory;      val process: Process?

    if ( directory != null ) processBuilder.directory( File(directory) )

    try { process = processBuilder.start() } catch ( e: IOException ) {

        warnUser( Translation.get("error.missing_ytdl") )
        warnUser( Translation.get("message.check_console") )

        e.printStackTrace();        return ""

    }

    val inputStream = process.inputStream
    val reader = BufferedReader( InputStreamReader(inputStream) )
    val stringBuilder = StringBuilder();    var s = reader.readLine()

    while ( s != null ) {

        stringBuilder.append(s)
        stringBuilder.append( System.getProperty("line.separator") )

        s = reader.readLine()

    }

    return stringBuilder.toString()

}

@Environment(EnvType.CLIENT)
fun deleteDownloads() {

    val directory = directories["streams"]!!;       val files = directory.listFiles()!!

    for ( file in files ) file.delete()

}