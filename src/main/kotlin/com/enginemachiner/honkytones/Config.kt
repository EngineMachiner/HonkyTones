package com.enginemachiner.honkytones

import com.enginemachiner.harmony.ConfigFile
import kotlin.math.max

object Config {

    @JvmField
    val SERVER = ServerConfigFile("server")

    fun server(): ServerData { return SERVER.data() }

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