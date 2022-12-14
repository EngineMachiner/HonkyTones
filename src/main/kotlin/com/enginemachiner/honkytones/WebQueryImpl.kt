package com.enginemachiner.honkytones

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.sapher.youtubedl.YoutubeDL
import com.sapher.youtubedl.YoutubeDLException
import com.sapher.youtubedl.YoutubeDLRequest
import com.sapher.youtubedl.YoutubeDLResponse
import com.sapher.youtubedl.mapper.VideoFormat
import com.sapher.youtubedl.mapper.VideoInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

object WebQueryImpl {}

/**
This implementation had to be done because the queries were
having issues on certain YouTube videos and due to the filesize
on the former class
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class VideoInfoImpl : VideoInfo() {

    var formats: ArrayList<VideoFormatImpl>? = null

    companion object {
        class VideoFormatImpl : VideoFormat() { var filesize: Long = 0 }
    }

}

private val mapper = ObjectMapper()
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
        printMessage( "There was an error parsing the data." )
        e.printStackTrace()
    }

    return null

}

@Deprecated("Check com.enginemachiner.honkytones.YTDLRequest")
fun executeSafely( request: YoutubeDLRequest): YoutubeDLResponse? {

    // Deny time-outs
    var isDone = false
    runBlocking { withTimeout( 6000 ) {

        val start = System.currentTimeMillis()
        val end = start + 5 * 1000
        while( true ) {
            if ( System.currentTimeMillis() > end ) {
                try { throw YoutubeDLException("") } catch ( _: YoutubeDLException ) {}
            }
            if (isDone || System.currentTimeMillis() > end) break
        }

    } }

    try {

        val response = YoutubeDL.execute(request)
        if ( response.out.isEmpty() ) throw YoutubeDLException("")
        isDone = true
        return response

    } catch ( e: YoutubeDLException) {

        printMessage( "URL is not valid or request took too long! Timing out." )

    }

    return null

}

class YTDLRequest(s: String) : YoutubeDLRequest(s) {

    public override fun buildOptions(): String {
        return super.buildOptions()
    }

}

fun executeYTDL(request: YTDLRequest): String {

    var path = clientConfig["ytdlPath"] as String
    path = getEnvPath( path, "PATH" )

    var command = listOf( "\"$path\"" )
    command = command + request.buildOptions().split(" ")
    val processBuilder = ProcessBuilder(command)

    if ( request.directory != null ) processBuilder.directory( File( request.directory ) )

    val process: Process?
    try { process = processBuilder.start()
    } catch ( e: IOException ) {
        e.printStackTrace()
        printMessage( "youtube-dl executable is missing or was denied!" )
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