package io.github.lambdallama

import java.io.DataOutputStream
import java.util.*

class TraceWriter(val stream: DataOutputStream) : TraceListener {
    companion object {
        const val HALT: Int = 0b1111_1111
        const val WAIT: Int = 0b1111_1110
        const val FLIP: Int = 0b1111_1101
        const val SMOVE: Int = 0b0000_0100
        const val LMOVE: Int = 0b0000_1100
        const val FILL: Int = 0b0000_0011
        const val VOID: Int = 0b0000_0010
        const val FISSION: Int = 0b0000_0101
        const val FUSION_P: Int = 0b0000_0111
        const val FUSION_S: Int = 0b0000_0110
        const val GFILL: Int = 0b0000_0001
        const val GVOID: Int = 0b0000_0000

        fun linearAxis(coord: Delta): Int {
            assert(coord.isLinear)
            if (coord.dx != 0) return 0b01
            if (coord.dy != 0) return 0b10
            return 0b11
        }

        fun shortLinear(coord: Delta): Int {
            assert(coord.isShortLinear)
            if (coord.dx != 0) return coord.dx + 5
            if (coord.dy != 0) return coord.dy + 5
            return coord.dz + 5
        }

        fun longLinear(coord: Delta): Int {
            assert(coord.isLongLinear)
            if (coord.dx != 0) return coord.dx + 15
            if (coord.dy != 0) return coord.dy + 15
            return coord.dz + 15
        }

        fun near(coord: Delta): Int {
            assert(coord.isNear)
            return (coord.dx + 1) * 9 + (coord.dy + 1) * 3 + (coord.dz + 1)
        }

        fun far(coord: Delta): Triple<Int, Int, Int> {
            assert(coord.isFar)
            return Triple(coord.dx + 30, coord.dy + 30, coord.dz + 30)
        }
    }

    override fun onStep(commands: SortedMap<Int, Command>) {
        for (pair in commands) {
            val command = pair.value
            when (command) {
                is Halt -> {
                    stream.writeByte(HALT)
                    stream.flush()
                }
                is Wait -> stream.writeByte(WAIT)
                is Flip -> stream.writeByte(FLIP)
                is SMove -> {
                    stream.writeByte(SMOVE
                            or (linearAxis(command.delta) shl 4))
                    stream.writeByte(longLinear(command.delta))
                }
                is LMove -> {
                    stream.writeByte(LMOVE
                            or (linearAxis(command.delta1) shl 6)
                            or (linearAxis(command.delta0) shl 4))
                    stream.writeByte(
                            (shortLinear(command.delta1) shl 4)
                            or shortLinear(command.delta0))
                }
                is Fill -> stream.writeByte(FILL
                        or (near(command.delta) shl 3))
                is Void -> stream.writeByte(VOID
                        or (near(command.delta) shl 3))
                is Fission -> {
                    stream.writeByte(FISSION
                            or (near(command.delta) shl 3))
                    stream.writeByte(command.m)
                }
                is FusionP -> stream.writeByte(FUSION_P
                        or (near(command.delta) shl 3))
                is FusionS -> stream.writeByte(FUSION_S
                        or (near(command.delta) shl 3))
                is GFill -> {
                    stream.writeByte(GFILL
                            or (near(command.dNear) shl 3))
                    val far = far(command.dFar)
                    stream.writeByte(far.first)
                    stream.writeByte(far.second)
                    stream.writeByte(far.third)
                }
                is GVoid -> {
                    stream.writeByte(GVOID
                            or (near(command.dNear) shl 3))
                    val far = far(command.dFar)
                    stream.writeByte(far.first)
                    stream.writeByte(far.second)
                    stream.writeByte(far.third)
                }
            }
        }
    }
}
