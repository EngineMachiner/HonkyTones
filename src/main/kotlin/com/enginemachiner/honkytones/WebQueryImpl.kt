package com.enginemachiner.honkytones

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.sapher.youtubedl.YoutubeDLRequest
import com.sapher.youtubedl.mapper.VideoFormat
import com.sapher.youtubedl.mapper.VideoInfo
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/**
    This implementation had to be done because the queries were
    having issues on certain YouTube videos and due to the filesize
    on the former class
*/
@Environment(EnvType.CLIENT)
@JsonIgnoreProperties(ignoreUnknown = true)
class VideoInfoImpl : VideoInfo() {

    var formats: ArrayList<VideoFormatImpl>? = null

    companion object {
        class VideoFormatImpl : VideoFormat() { var filesize: Long = 0 }
    }

}

@Environment(EnvType.CLIENT)
private val mapper = ObjectMapper()

@Environment(EnvType.CLIENT)
fun getVideoInfo(path: String): VideoInfo? {

    val request = YTDLRequest(path)
    request.setOption("youtube-skip-dash-manifest")
    request.setOption("dump-json")
    request.setOption("no-playlist")
    request.setOption("no-mark-watched")
    request.setOption("no-colors")
    request.setOption("no-download-archive")
    request.setOption("wait-for-video 5")
    request.setOption("skip-download")

    val response = executeYTDL(request)
    if ( response.isEmpty() ) return null

    try { return mapper.readValue( response, VideoInfoImpl::class.java )
    } catch (e: Exception) {
        printMessage( Translation.get("honkytones.error.parse") )
        printMessage( Translation.get("honkytones.message.check_console") )
        e.printStackTrace()
    }

    return null

}

@Environment(EnvType.CLIENT)
class YTDLRequest(s: String) : YoutubeDLRequest(s) {

    public override fun buildOptions(): String {
        return super.buildOptions()
    }

}

@Environment(EnvType.CLIENT)
fun executeYTDL( request: YTDLRequest ): String {

    var path = clientConfig["ytdl-Path"] as String
    path = getEnvPath( path, "PATH" )

    var command = listOf( "\"$path\"" )
    command = command + request.buildOptions().split(" ")
    val processBuilder = ProcessBuilder(command)

    if ( request.directory != null ) processBuilder.directory( File( request.directory ) )

    val process: Process?
    try { process = processBuilder.start()
    } catch ( e: IOException ) {
        printMessage( Translation.get("honkytones.error.missing_yt-dl") )
        printMessage( Translation.get("honkytones.message.check_console") )
        e.printStackTrace()
        return ""
    }

    val reader = BufferedReader( InputStreamReader( process.inputStream ) )
    val stringBuilder = StringBuilder()
    var s = reader.readLine()

    while ( s != null ) {
        stringBuilder.append( s )
        stringBuilder.append( System.getProperty("line.separator") )
        s = reader.readLine()
    }

    return stringBuilder.toString()

}