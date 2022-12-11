package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.FabricLoaderImpl
import java.io.File
import java.nio.file.Paths
import java.util.*

object FileImpl {}

private fun iterateDirs( key: String, formerPath: String, dirs: List<String> ): String? {

    for ( dir in dirs ) {

        val iterPath = Paths.get( dir, formerPath )

        val temp = "$iterPath"
            .replace("\$$key\\", "")
            .replace("\$$key/", "")

        if ( File(temp).canExecute() ) { return temp }

    }

    return null

}

fun getEnvPath(path: String, envKey: String ): String {

    if ( path.startsWith( "\$" + envKey ) ) {

        val sysPath = System.getenv(envKey)

        // Windows and Linux separators
        for ( separator in listOf( ";", ":" ) ) {
            val dirs = sysPath.split(separator)
            val envPath = iterateDirs( envKey, path, dirs )
            if ( envPath != null ) return envPath
        }

    }

    return path

}

open class RestrictedFile( s: String ) : File(s) {

    init {

        val parse = s.replace("/", "\\")
        if ( !parse.startsWith(Base.MOD_NAME + "\\") && parse.isNotEmpty() ) {
            printMessage( "Access Denied" )
            setExecutable(false);     setReadable(false);     setWritable(false)
        }

    }

}

open class ConfigFile( s: String ): RestrictedFile( configPath + s ) {

    val properties = Properties()
    private var shouldCreate = true

    init { shouldCreate = verify(shouldCreate);    creation() }

    open fun verify( shouldCreate: Boolean ): Boolean { return shouldCreate }

    private fun creation() {

        val dir = RestrictedFile(configPath)
        if ( !dir.exists() ) dir.mkdirs()

        if ( !shouldCreate ) return
        if ( !exists() || length() == 0L ) createNewFile()
        else properties.load( inputStream() )
        setDefaultProperties()

    }

    override fun createNewFile(): Boolean {
        val b = super.createNewFile()
        properties.load( inputStream() )
        return b
    }

    companion object {
        private const val configPath = Base.MOD_NAME + "/config/"
    }

    open fun setDefaultProperties() {
        properties.store( outputStream(), "\n HonkyTones main configuration \n" )
    }

    fun updateProperties(map: Map<String, Any> ) {

        for ( entry in map ) properties.setProperty(entry.key, "${entry.value}")
        properties.store( outputStream(), "\n HonkyTones main configuration \n" )

    }

}

class ClientConfigFile(path: String) : ConfigFile(path) {

    override fun verify( shouldCreate: Boolean ): Boolean {
        if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return false
        return shouldCreate
    }

    override fun setDefaultProperties() {

        val default = mapOf(
            "ffmpegDir" to "",       "ytdlPath" to "yt-dlp",
            "mobsParticles" to "true",       "writeDeviceInfo" to "true",
            "playerParticles" to "true",     "keep_downloads" to "false",
            "keep_videos" to "false",       "audio_quality" to "5",
            "max_length" to "1200" // 60 * 20 -> 20 min
        )

        for ( pair in default ) {
            if ( !properties.containsKey(pair.key) ) properties.setProperty( pair.key, pair.value )
        }

        super.setDefaultProperties()

    }

}

class ServerConfigFile(path: String) : ConfigFile(path) {

    override fun setDefaultProperties() {

        val default = mapOf(
            "debugMode" to "false",       "mobsPlayingDelay" to "120",
            "mobsParticles" to "true",     "playerParticles" to "true",
            "allowPushingPlayers" to "false"
        )

        for ( pair in default ) {
            if ( !properties.containsKey(pair.key) ) properties.setProperty( pair.key, pair.value )
        }

        super.setDefaultProperties()

    }

}