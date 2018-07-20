package io.github.lambdallama

import java.io.DataInputStream

class TraceReader(val stream: DataInputStream) {
    private fun shortLinear(axis: Int, v: Int): DeltaCoord {
        return when (axis) {
            0b01 -> DeltaCoord(v - 5, 0 ,0)
            0b10 -> DeltaCoord(0, v - 5, 0)
            0b11 -> DeltaCoord(0, 0, v - 5)
            else -> throw Exception("Invalid input stream")
        }
    }

    private fun longLinear(axis: Int, v: Int): DeltaCoord {
        return when (axis) {
            0b01 -> DeltaCoord(v - 15, 0 ,0)
            0b10 -> DeltaCoord(0, v - 15, 0)
            0b11 -> DeltaCoord(0, 0, v - 15)
            else -> throw Exception("Invalid input stream")
        }
    }

    private fun near(v: Int): DeltaCoord {
        val dz = (v % 3) - 1
        val v0 = v / 3
        val dy = (v0 % 3) - 1
        val v1 = v0 / 3
        val dx = v1 - 1
        return DeltaCoord(dx, dy, dz)
    }

    fun readAllCommands(): Sequence<Command> {
        val commands : ArrayList<Command> = ArrayList()

        while (stream.available() > 0) {
            val byte = stream.read()
            if (byte == TraceWriter.HALT) {
                commands.add(Halt)
            } else if (byte == TraceWriter.WAIT) {
                commands.add(Wait)
            } else if (byte == TraceWriter.FLIP) {
                commands.add(Flip)
            } else when (byte and TraceWriter.FUSION_P) {
                TraceWriter.SMOVE -> {
                    if ((byte shr 3) % 2 == 0) {
                        val axis = byte shr 4
                        val v = stream.read()
                        commands.add(SMove(longLinear(axis, v)))
                    } else {
                        val axis0 = byte shr 4
                        val axis1 = byte shr 6
                        val byte1 = stream.read()
                        val v0 = (byte1 shl 4) shr 4
                        val v1 = byte1 shr 4
                        commands.add(LMove(shortLinear(axis0, v0), shortLinear(axis1, v1)))
                    }
                }
                TraceWriter.FILL -> {
                    commands.add(Fill(near(byte shr 3)))
                }
                TraceWriter.FISSION -> {
                    commands.add(Fission(near(byte shr 3), stream.read()))
                }
                TraceWriter.FUSION_P -> {
                    commands.add(FusionP(near(byte shr 3)))
                }
                TraceWriter.FUSION_S -> {
                    commands.add(FusionS(near(byte shr 3)))
                }
                else -> throw Exception("Invalid input stream")
            }
        }

        return commands.asSequence()
    }
}
