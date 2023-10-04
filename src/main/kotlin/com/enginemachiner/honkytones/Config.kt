package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.io.File
import java.util.*

@JvmField
@Environment(EnvType.CLIENT)
val clientConfig = mutableMapOf<String, Any>()

@JvmField
val serverConfig = mutableMapOf<String, Any>()

@JvmField
@Environment(EnvType.CLIENT)
val clientConfigFile = ClientConfigFile("client.txt")

@JvmField
val serverConfigFile = ServerConfigFile("server.txt")

@Environment(EnvType.CLIENT)
val clientConfigKeys = mutableMapOf(

    Boolean::class to listOf(
        "keep_downloads", "keep_videos", "mob_particles", "write_device_info",
        "player_particles", "listen_all", "music_particles"
    ),

    Int::class to listOf( "audio_quality", "max_length" ),

    String::class to listOf( "ffmpeg_directory", "youtube-dl_path" )

)

val serverConfigKeys = mutableMapOf(

    Boolean::class to listOf(
        "mob_particles", "player_particles", "allow_pushing_players",
        "music_particles"
    ),

    Int::class to listOf("mobs_playing_delay")

)

@Environment(EnvType.CLIENT)
fun readClientConfig() {

    val boolKeys = clientConfigKeys[ Boolean::class ]!!
    for ( key in boolKeys ) {
        clientConfig[key] = clientConfigFile.properties.getProperty(key).toBoolean()
    }

    val intKeys = clientConfigKeys[ Int::class ]!!
    for ( key in intKeys ) {

        val value = clientConfigFile.properties.getProperty(key).toInt()

        clientConfig[key] = value

        if ( key == "audio_quality" && value !in (0..10) ) clientConfig[key] = 5

        if ( key == "max_length" && value <= 0 ) clientConfig[key] = 120

    }

    val stringKeys = clientConfigKeys[ String::class ]!!
    for ( key in stringKeys ) {
        clientConfig[key] = clientConfigFile.properties.getProperty(key)
    }

}

fun readServerConfig() {

    val boolKeys = serverConfigKeys[ Boolean::class ]!!
    for ( key in boolKeys ) {
        serverConfig[key] = serverConfigFile.properties.getProperty(key).toBoolean()
    }

    val intKeys = serverConfigKeys[ Int::class ]!!
    for ( key in intKeys ) {

        val value = serverConfigFile.properties.getProperty(key).toInt()

        serverConfig[key] = value

        if ( key == "mobs_playing_delay" && value < 120 ) serverConfig[key] = 120

    }


}

open class ConfigFile( s: String ): File( CONFIG_DIRECTORY + s ) {

    private var shouldCreate = true;        val properties = Properties()

    init { create() }

    open fun defaults(): Map<String, String> { return default }

    open fun verify(shouldCreate: Boolean): Boolean { return shouldCreate }

    fun setDefaultProperties() {

        for ( pair in defaults() ) {

            val key = pair.key
            val hasKey = properties.containsKey(key)
            if ( !hasKey ) properties.setProperty( key, pair.value )

        }

        store()

    }

    fun updateProperties( map: Map<String, Any> ) {

        for ( entry in map ) properties.setProperty( entry.key, entry.value.toString() )

        store()

    }

    private fun create() {

        shouldCreate = verify(shouldCreate);    if ( !shouldCreate ) return

        if ( !exists() || length() == 0L ) createNewFile()

        properties.load( inputStream() );   setDefaultProperties()

    }

    private fun store() {

        properties.store( outputStream(), "\n HonkyTones main configuration. \n" )

    }

    companion object {

        private const val CONFIG_DIRECTORY = "config/$MOD_NAME/"

        private val default = mapOf<String, String>()

        fun checkConfigDirectory() {

            val dir = File(CONFIG_DIRECTORY);   if ( !dir.exists() ) dir.mkdirs()

        }

    }

}

class ClientConfigFile(path: String) : ConfigFile(path) {

    override fun defaults(): Map<String, String> { return default }

    override fun verify( shouldCreate: Boolean ): Boolean {
        if ( !isClient() ) return false;        return shouldCreate
    }

    companion object {

        private val default = mapOf(
            "listen_all" to "false",              "music_particles" to "true",
            "ffmpeg_directory" to "",           "youtube-dl_path" to "youtube-dl",
            "mob_particles" to "true",          "write_device_info" to "true",
            "player_particles" to "true",       "keep_downloads" to "false",
            "keep_videos" to "false",           "audio_quality" to "5",
            "max_length" to "1200" // 60 * 20 -> 20 min
        )

    }

}

class ServerConfigFile(path: String) : ConfigFile(path) {

    override fun defaults(): Map<String, String> { return default }

    companion object {

        val default = mapOf(
            "music_particles" to "true",
            "mobs_playing_delay" to "120",
            "mob_particles" to "true",     "player_particles" to "true",
            "allow_pushing_players" to "false"
        )

    }

}