package com.enginemachiner.honkytones

import java.io.File
import java.io.FileWriter
import java.util.*

object FileImpl {}

open class RestrictedFile( s: String ) : File(s) {

    init {
        if ( !path.startsWith(Base.MOD_NAME + "\\") && path.isNotEmpty() ) {
            printMessage( "Access Denied" )
            setExecutable(false);     setReadable(false);     setWritable(false)
        }
    }

}

open class ConfigFile( s: String ): RestrictedFile( configPath + s ) {

    val properties = Properties()

    init {
        val dir = RestrictedFile(configPath)
        if ( !dir.exists() ) dir.mkdir()
        if ( !exists() || length() == 0L ) createNewFile()
        else properties.load( inputStream() )
    }

    override fun createNewFile(): Boolean {

        val b = super.createNewFile()

        properties.load( inputStream() )
        storeProperties()

        return b

    }

    companion object {
        private const val configPath = Base.MOD_NAME + "/config/"
    }

    open fun storeProperties() {
        properties.store( FileWriter(path), "\n HonkyTones main configuration \n" )
    }

    fun updateProperties( map: Map<String, Any> ) {
        for ( entry in map ) properties.setProperty(entry.key, "${entry.value}")
        storeProperties()
    }

}

class ClientConfigFile(path: String) : ConfigFile(path) {

    override fun storeProperties() {

        properties.setProperty("mobsParticles", "true")
        properties.setProperty("writeDeviceInfo", "true")
        properties.setProperty("playerParticles", "true")
        properties.setProperty("keep_downloads", "false")
        properties.setProperty("keep_videos", "false")
        properties.setProperty("audio_quality", "5")

        super.storeProperties()

    }

}

class ServerConfigFile(path: String) : ConfigFile(path) {

    override fun storeProperties() {

        properties.setProperty("debugMode", "false")
        properties.setProperty("mobsPlayingDelay", "120")
        properties.setProperty("mobsParticles", "true")
        properties.setProperty("playerParticles", "true")
        properties.setProperty("allowPushingPlayers", "false")

        super.storeProperties()

    }

}