package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import com.enginemachiner.honkytones.items.instruments.Keyboard
import com.enginemachiner.honkytones.items.instruments.SFX
import kotlin.reflect.KClass

private typealias map = MutableMap<KClass<out InstrumentItem>, Set<String> >

object MusicTheory {

    val noteMap: map = mutableMapOf()


    private val notes = mutableSetOf("C","D","E","F","G","A","B")

    val sharpsToFlats = mapOf("C#" to "D_",   "D#" to "E_",   "F#" to "G_",   "G#" to "A_",   "A#" to "B_")


    val octave = mutableSetOf<String>()

    private val twoOctaves = mutableListOf<String>()

    var completeSet = setOf<String>()


    init {

        // Single octave, no range.

        for ( note in notes ) {

            if ( note != "C" && note != "F" ) octave.add( note + "_" )

            octave.add(note)

        }


        // Two octaves, no range.

        val temp = builder( octave, 1..2 )

        for ( t in temp ) {

            val s = t.replace( "1", "" )
                .replace( "2", "" )

            twoOctaves.add(s)

        }


        // A complete set of octaves with ranges.

        completeSet = builder( octave, -1..8 )

    }


    fun noteCount(): Int { return completeSet.size }

    /** Move a semitone distance up an octave (12 semitones) */
    fun shift( a: Int, b: Int ): Int { if (a < b) return a + 12;   return a }

    /** Get the note position in two octaves. */
    // @Environment(EnvType.CLIENT)
    fun index(s: String): Int {

        for ( note in twoOctaves ) {

            // Find index by note range.
            val find = s.replace( Regex("-?\\d"), "" )

            if ( find == note ) return twoOctaves.indexOf(note)

        }

        return -1

    }

    /** Builds the sound data. */
    fun build() {

        // Sound file path sets.

        // Most generic set.
        val set1 = builder( setOf( "C4-E4_", "E4-G4", "A4_-B4" ), 4..5 );       set1.add("C6")

        val set2 = builder( octave, 3..5 )

        val set3 = builder( octave, 2..2 ) + setOf( "C3", "D3_", "D3", "E3_" )

        val set4 = builder( octave, 3..6 ) - setOf( "C3", "D3_", "D3", "A4" ) + setOf("C7")


        InstrumentItem.classes.forEach {

            var set: Set<String> = set1

            when(it) {

                Keyboard::class -> set = set2
                DrumSet::class -> set = set3
                SFX::class -> set = set4

            }

            noteMap[it] = set

        }

    }

    /** Parse and build according to template and range. **/
    private fun builder( template: Set<String>, range: IntRange ): MutableSet<String>{

        val output = mutableSetOf<String>()

        for ( n in range ) {    for ( t in template ) {

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