import com.enginemachiner.honkytones.printMessage
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

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoInfoImpl : VideoInfo() {

    var formats: ArrayList<VideoFormatImpl>? = null

    companion object {
        class VideoFormatImpl : VideoFormat() { var filesize: Long = 0 }
    }

}

private val mapper = ObjectMapper()
fun getVideoInfo(path: String): VideoInfo? {

    val request = YTDLPRequest(path)
    request.setOption("youtube-skip-dash-manifest")
    request.setOption("dump-json")
    request.setOption("no-playlist")
    request.setOption("no-mark-watched")
    request.setOption("no-colors")
    request.setOption("no-download-archive")
    request.setOption("wait-for-video 5")
    request.setOption("skip-download")

    val response = executeYTDLP(request)
    if ( response.isEmpty() ) return null

    try { return mapper.readValue( response, VideoInfoImpl::class.java )
    } catch (e: Exception) {
        printMessage( "There was an error parsing the data." )
        e.printStackTrace()
    }

    return null

}

@Deprecated("Check YTDLPRequest")
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

class YTDLPRequest(s: String) : YoutubeDLRequest(s) {

    public override fun buildOptions(): String {
        return super.buildOptions()
    }

}

fun executeYTDLP( request: YTDLPRequest ): String {

    val path = "youtube-dl"
    val command = path + " " + request.buildOptions()
    val processBuilder = ProcessBuilder( command.split(" ") )

    if ( request.directory != null ) processBuilder.directory( File( request.directory ) )

    val process: Process?
    try { process = processBuilder.start()
    } catch ( _: IOException ) {
        printMessage( "youtube-dl executable is missing!" )
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