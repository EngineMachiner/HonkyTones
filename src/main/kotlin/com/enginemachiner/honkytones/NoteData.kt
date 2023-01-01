package com.enginemachiner.honkytones

object NoteData {

    // Sound file structure
    val soundsMap = mutableMapOf< String, Set<String> >()

    private val notes = mutableSetOf("C","D","E","F","G","A","B")
    val octave = mutableSetOf<String>()
    val twoOctaves = mutableListOf<String>()
    var wholeNoteSet = setOf<String>()

    val sharpsMap = mapOf(
        "C#" to "D_",   "D#" to "E_",   "F#" to "G_",
        "G#" to "A_",   "A#" to "B_"
    )

    fun buildSoundMap() {

        // Build a single octave with all notes
        for (note in notes) {
            if (note != "C" && note != "F") octave.add(note + "_")
            octave.add(note)
        }

        // A two octaves list to get relative semitone positions
        val tempTwo = builder(octave, setOf(1, 2))
        for (t in tempTwo) {
            val s = t.replace("1", "")
                .replace("2", "")
            twoOctaves.add(s)
        }

        // And a complete whole note set for midi messages references
        val wholeRange = mutableSetOf<Int>()
        for (i in -1..8) wholeRange.add(i)
        wholeNoteSet = builder(octave, wholeRange)

        // Start adding the set with the sound structure to the map
        val soundsSet4 = setOf("B2-D3", "E3_-G3_", "G3-B3_", "B3-D4")

        // Drum set setup
        val drumSet = builder(octave, setOf(2)) as MutableSet<String>
        val tempDrumSet = mutableSetOf("C3", "D3_", "D3", "E3_")

        for (n in tempDrumSet) drumSet.add(n)
        soundsMap["drumset"] = drumSet

        soundsMap["organ"] = setOf("C3-E3_", "E3-G3", "A3_-B3", "B3")
        soundsMap["harp"] = builder(soundsMap["organ"], setOf(4))

        // Violin setup
        val tempViolin = builder(soundsMap["harp"], setOf(3)) as MutableSet<String>
        tempViolin.add("C6"); soundsMap["violin"] = tempViolin

        soundsMap["keyboard"] = builder(octave, setOf(3, 4, 5, 6))
        soundsMap["electricguitar"] = builder(octave, setOf(4))
        soundsMap["acousticguitar"] = soundsMap["organ"]!!.toSet()
        soundsMap["electricguitar-clean"] = soundsMap["organ"]!!.toSet()
        soundsMap["viola"] = builder(soundsSet4, setOf(3, 6))
        soundsMap["flute"] = soundsMap["harp"]!!.toSet()
        soundsMap["oboe"] = soundsMap["harp"]!!.toSet()
        soundsMap["trombone"] = soundsMap["harp"]!!.toSet()

    }

    private fun builder(template: Set<String>?, range: Set<Int>): Set<String>{

        val newSet = mutableSetOf<String>()

        for ( r in range ) {

            val r2 = r.toString()

            for ( t in template!! ) {

                if ( t.contains("-") ) {

                    val first = template.first()
                        .substringAfter("-")[1].toString()

                    // Sort the pair values according to the range given
                    val start = t.substringBefore("-")[1].toString()
                    val end = t.substringAfter("-")[1].toString()
                    var start2 = r2;     var end2 = r2

                    if ( start.toInt() < end.toInt() ) {
                        start2 = (r2.toInt() - 1).toString()
                    }

                    if ( first.toInt() < end.toInt() ) {
                        val dif = end.toInt() - first.toInt()
                        start2 = (start2.toInt() + dif).toString()
                        end2 = (end2.toInt() + dif).toString()
                    }

                    newSet.add( t.replace( start, start2 ).replace( end, end2 ) )

                } else {

                    val s = t.filter { !it.isDigit() };     var s2 = s + r2
                    if ( s.contains("_") ) { s2 = s[0] + r2 + s[1] }
                    newSet.add(s2)

                }

            }

        }

        return newSet

    }

}