package io.github.lambdallama

import com.google.common.math.IntMath
import java.io.File
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.inv
import kotlin.math.abs
import kotlin.math.max


data class Coord(val x: Int, val y: Int, val z: Int) {
    companion object {
        val ZERO = Coord(0, 0, 0)
    }

    val volume: Int get() = x * y * z

    fun isInBounds(bounds: Coord): Boolean {
        return x < bounds.x && y < bounds.y && z < bounds.z
    }

    operator fun plus(delta: DeltaCoord): Coord {
        return Coord(x + delta.dx, y + delta.dy, z + delta.dz)
    }

    operator fun minus(coord: Coord): DeltaCoord {
        return DeltaCoord(x - coord.x, y - coord.y, z - coord.z)
    }
}

data class DeltaCoord(val dx: Int, val dy: Int, val dz: Int) {
    val mlen: Int get() = abs(dx) + abs(dy) + abs(dz)
    val clen: Int get() = max(max(abs(dx), abs(dy)), abs(dz))

    val isLinear: Boolean get() = ((dx != 0) xor (dy != 0) xor (dz != 0)) &&
            ((dx == 0) || (dy == 0) || (dz == 0))
    val isShortLinear: Boolean get() = isLinear && mlen <= 5
    val isLongLinear: Boolean get() = isLinear && mlen <= 15
    val isNear: Boolean get() = mlen in 1..2 && clen == 1
}

data class Matrix(val R: Int, val coordinates: ByteArray) {
    /** Returns true if the voxel at a given coordinate is Full. */
    operator fun get(x: Int, y: Int, z: Int): Boolean {
        val offset = x * R * R + y * R + z
        val b = coordinates[offset / 8].toInt() and 0xff
        return b and (1 shl (offset % 8)) != 0
    }

    operator fun set(x: Int, y: Int, z: Int, value: Boolean) {
        val offset = x * R * R + y * R + z
        val mask = 1 shl (offset % 8)
        val b = coordinates[offset / 8].toInt() and 0xff
        coordinates[offset / 8] = if (value) {
            (b or mask).toByte()
        } else {
            (b and mask.inv()).toByte()
        }
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is Matrix -> false
            else -> R == other.R && Arrays.equals(coordinates, other.coordinates)
        }
    }

    override fun hashCode(): Int = 31 * R + Arrays.hashCode(coordinates)

    val isWellFormed: Boolean get() {
        for (x in 0 until R) {
            for (y in 0 until R) {
                for (z in 0 until R) {
                    if (this[x, y, z] &&
                        !(x >= 1 && x <= R - 2 &&
                            y >= 0 && y <= R - 2 &&
                            z >= 1 && z <= R - 2)) {
                        return false
                    }
                }
            }
        }

        fun go(initial: Coord, seen: Matrix) {
            val q = ArrayDeque<Coord>()
            q.add(initial)
            while (q.isNotEmpty()) {
                val c = q.pop()
                seen[c.x, c.y, c.z] = true

                for (dxdydz in DXDYDZ_MLEN1) {
                    val n = c + dxdydz
                    if (n.x in 0..(R - 1) && n.y in 0..(R - 1) && n.z in 0..(R - 1)
                        && this[n.x, n.y, n.z] && !seen[n.x, n.y, n.z]) {
                        q.add(n)
                    }
                }
            }
        }

        // y == 0 i.e. start from the ground.
        val seen = copy(coordinates = ByteArray(coordinates.size))
        for (x in 0 until R) {
            for (z in 0 until R) {
                if (this[x, 0, z] && !seen[x, 0, z]) {
                    go(Coord(x, 0, z), seen)
                }
            }
        }

        return seen == this
    }

    companion object {
        val DXDYDZ_MLEN1: Array<DeltaCoord> = arrayOf(
            DeltaCoord(0, 0, 1),
            DeltaCoord(0, 1, 0),
            DeltaCoord(1, 0, 0),
            DeltaCoord(0, 0, -1),
            DeltaCoord(0, -1, 0),
            DeltaCoord(-1, 0, 0))
    }
}

data class Model(val matrix: Matrix) {
    companion object {
        fun parse(path: File): Model {
            val buf = ByteBuffer.wrap(path.readBytes())
            val R = buf.get().toInt()
            val shape = IntMath.divide(R * R * R, 8, RoundingMode.CEILING)
            val coordinates = ByteArray(shape).apply { buf.get(this) }
            if (path.extension == "xmdl") {
                TODO()
            }

            return Model(Matrix(R, coordinates))
        }
    }
}
