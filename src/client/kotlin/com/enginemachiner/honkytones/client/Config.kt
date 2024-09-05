package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.ConfigFile

object Config {

    @JvmField
    val CLIENT = ClientConfigFile("client")

    fun client(): ClientData { return CLIENT.data() }

}


private typealias ClientData = ClientConfigFile.Companion.Data

class ClientConfigFile(path: String) : ConfigFile<ClientData>( path, Data::class ) {

    override fun setDefaults() {

        data = Data();       map = json( data, map::class );        write()

    }

    override fun check() {

        val length = data!!.maxDuration

        if ( length <= 0 ) data!!.maxDuration = MAX_DURATION

    }

    companion object {

        const val MAX_DURATION = 600 // 60 * 10 -> 10 min

        data class Data(

            var listenAll: Boolean = false,
            var musicParticles: Boolean = true,
            var mobParticles: Boolean = true,
            var writeDeviceName: Boolean = true,
            var playerParticles: Boolean = true,
            var keepDownloads: Boolean = false,
            var keepVideos: Boolean = false,

            var ffmpegDirectory: String = "",
            var ytdlpPath: String = "yt-dlp.exe",

            var maxDuration: Int = MAX_DURATION

        )

    }

}