package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.instruments.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import kotlin.reflect.KClass

object MusicTheory {

    val instrumentFiles = mutableMapOf< KClass<out Instrument>, Set<String> >()

    private val notes = mutableSetOf("C","D","E","F","G","A","B")

    val sharpsToFlats = mapOf(
        "C#" to "D_",   "D#" to "E_",   "F#" to "G_",   "G#" to "A_",   "A#" to "B_"
    )

    val octave = mutableSetOf<String>()

    private val twoOctaves = mutableListOf<String>()

    var completeSet = setOf<String>()

    fun noteCount(): Int { return completeSet.size }

    /** Move a semitone distance up an octave (12 semitones) */
    fun shift( a: Int, b: Int ): Int { if (a < b) return a + 12;   return a }

    /** Get the note position in a two octaves. */
    @Environment(EnvType.CLIENT)
    fun index(s: String): Int {

        for ( note in twoOctaves ) {

            // Find index by note range.
            val find = s.replace( Regex("-?\\d"), "" )

            if ( find == note ) return twoOctaves.indexOf(note)

        }

        return -1

    }

    fun buildSoundData() {

        // Build a single octave (no range).
        for ( note in notes ) {

            if ( note != "C" && note != "F" ) octave.add( note + "_" )

            octave.add(note)

        }

        // Build two octaves (no range).
        val temp = builder( octave, setOf( 1, 2 ) )
        for ( t in temp ) {

            val s = t.replace("1", "")
                .replace("2", "")

            twoOctaves.add(s)

        }

        // A complete set of octaves with ranges.
        completeSet = builder( octave, ( -1..8 ).toSet() )

        // Sets to be added to the map used by some instrument sound files paths.
        val set1 = setOf( "B2-D3", "E3_-G3_", "G3-B3_", "B3-D4" )
        val set2 = setOf( "C3-E3_", "E3-G3", "A3_-B3", "B3" )

        // Based for percussion.
        val set3 = builder( octave, setOf(2) ) as MutableSet<String>
        for ( n in mutableSetOf( "C3", "D3_", "D3", "E3_" ) ) set3.add(n)

        instrumentFiles[ DrumSet::class ] = set3

        instrumentFiles[ Organ::class ] = set2
        instrumentFiles[ AcousticGuitar::class ] = set2
        instrumentFiles[ ElectricGuitarClean::class ] = set2

        instrumentFiles[ Viola::class ] = builder( set1, setOf( 3, 6 ) )

        instrumentFiles[ Keyboard::class ] = builder( octave, ( 3..6 ).toSet() )

        instrumentFiles[ ElectricGuitar::class ] = builder( octave, setOf(4) )

        val set4 = builder( set2, setOf(4) )

        instrumentFiles[ Harp::class ] = set4;          instrumentFiles[ Oboe::class ] = set4
        instrumentFiles[ Recorder::class ] = set4;      instrumentFiles[ Trombone::class ] = set4

        val set5 = builder( set4, setOf(3) ) as MutableSet<String>;     set5.add("C6")
        instrumentFiles[ Violin::class ] = set5

    }

    /** Parse and build according to template and range. **/
    private fun builder( template: Set<String>?, range: Set<Int> ): Set<String>{

        val output = mutableSetOf<String>()

        for ( n in range ) {    for ( t in template!! ) {

            var s: String

            if ( t.contains('-') ) {

                // If the sound will be pitched.
                // Sort the pair values according to the range given.

                val first = template.first().substringAfter('-')[1].toString()

                val start = t.substringBefore("-")[1].toString()
                val end = t.substringAfter("-")[1].toString()
                var start2 = n;     var end2 = n

                if ( start.toInt() < end.toInt() ) start2 = n - 1

                if ( first.toInt() < end.toInt() ) {

                    val dif = end.toInt() - first.toInt()

                    start2 += dif;      end2 += dif

                }

                s = t.replace( start, "$start2" )
                    .replace( end, "$end2" )

            } else {

                // If the sound won't change pitch.

                val s2 = t.filter { !it.isDigit() }
                s = if ( s2.contains('_') ) t[0] + "$n" + "_" else s2 + n

            }

            output.add(s)

        }   }

        return output

    }

}
