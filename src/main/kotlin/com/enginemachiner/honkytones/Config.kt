package com.enginemachiner.honkytones

import com.enginemachiner.harmony.ConfigFile
import com.enginemachiner.harmony.isClient
import kotlin.math.max

object Config {

    @JvmField // @Environment(EnvType.CLIENT)
    val CLIENT = ClientConfigFile("client")

    // @Environment(EnvType.CLIENT)
    fun client(): ClientData { return CLIENT.data() }


    @JvmField
    val SERVER = ServerConfigFile("server")

    fun server(): ServerData { return SERVER.data() }

}

private typealias ClientData = ClientConfigFile.Companion.Data

// @Environment(EnvType.CLIENT)
class ClientConfigFile(path: String) : ConfigFile<ClientData>( path, Data::class ) {

    override fun canCreateFile(): Boolean { return isClient() }

    override fun setDefaults() {

        data = Data();       map = json( data, map::class );        write()

    }

    override fun check() {

        val length = data!!.maxLength

        if ( length <= 0 ) data!!.maxLength = MAX_LENGTH

    }

    companion object {

        const val MAX_LENGTH = 600 // 60 * 10 -> 10 min

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

            var maxLength: Int = MAX_LENGTH

        )

    }

}


private typealias ServerData = ServerConfigFile.Companion.Data

class ServerConfigFile(path: String) : ConfigFile<ServerData>( path, Data::class ) {

    override fun setDefaults() {

        data = Data();       map = json( data, map::class );        write()

    }

    override fun check() {

        val delay = data!!.mobsPlayingDelay

        data!!.mobsPlayingDelay = max( delay, MOBS_PLAYING_DELAY )

    }

    companion object {

        const val MOBS_PLAYING_DELAY = 120

        data class Data(

            var musicParticles: Boolean = true,
            var mobParticles: Boolean = true,
            var playerParticles: Boolean = true,
            var allowPushingPlayers: Boolean = false,

            var mobsPlayingDelay: Int = MOBS_PLAYING_DELAY

        )

    }

}