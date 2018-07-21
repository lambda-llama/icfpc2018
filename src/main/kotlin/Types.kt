package io.github.lambdallama

import com.google.common.collect.ComparisonChain
import com.google.common.collect.Ordering
import com.google.common.math.IntMath
import java.io.File
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.*
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.abs
import kotlin.math.max


data class Coord(val x: Int, val y: Int, val z: Int) : Comparable<Coord> {
    val volume: Int get() = x * y * z

    fun isInBounds(R: Int): Boolean {
        return x in 0 until R && y in 0 until R && z in 0 until R
    }

    operator fun plus(delta: DeltaCoord): Coord {
        return Coord(x + delta.dx, y + delta.dy, z + delta.dz)
    }

    operator fun minus(delta: DeltaCoord): Coord {
        return this + (-delta)
    }

    operator fun minus(coord: Coord): DeltaCoord {
        return DeltaCoord(x - coord.x, y - coord.y, z - coord.z)
    }

    override fun compareTo(other: Coord): Int = ComparisonChain.start()
        .compare(x, other.x)
        .compare(y, other.y)
        .compare(z, other.z)
        .result()

    override fun toString(): String {
        return "($x,$y,$z)"
    }

    companion object {
        val ZERO = Coord(0, 0, 0)
    }
}

data class DeltaCoord(val dx: Int, val dy: Int, val dz: Int) : Comparable<DeltaCoord> {
    operator fun unaryMinus() = DeltaCoord(-dx, -dy, -dz)

    operator fun times(s: Int) = DeltaCoord(dx * s, dy * s, dz * s)

    val mlen: Int get() = abs(dx) + abs(dy) + abs(dz)
    val clen: Int get() = max(max(abs(dx), abs(dy)), abs(dz))

    val isLinear: Boolean get() = ((dx != 0) xor (dy != 0) xor (dz != 0)) &&
            ((dx == 0) || (dy == 0) || (dz == 0))
    val isShortLinear: Boolean get() = isLinear && mlen <= 5
    val isLongLinear: Boolean get() = isLinear && mlen <= 15
    val isNear: Boolean get() = mlen in 1..2 && clen == 1

    override fun compareTo(other: DeltaCoord): Int = ComparisonChain.start()
        .compare(dx, other.dx)
        .compare(dy, other.dy)
        .compare(dz, other.dz)
        .result()

    override fun toString(): String {
        return "<$dx,$dy,$dz>"
    }
}

data class Matrix(val R: Int, val coordinates: ByteArray) {
    /** All coords reachable from a given one via SMove. */
    fun sNeighborhood(coord: Coord): Sequence<Pair<SMove, Coord>> = buildSequence {
        for (delta in DXDYDZ_LLD) {
            val n = coord + delta
            if (n.isInBounds(R) && isVoidRegion(coord, n)) {
                yield(SMove(delta) to n)
            }
        }
    }

    /** All coords reachable from a given one via LMove. */
    fun lNeighborhood(coord: Coord): Sequence<Pair<LMove, Coord>> = buildSequence {
        for (delta1 in DXDYDZ_SLD) {
            val n1 = coord + delta1
            if (n1.isInBounds(R) && isVoidRegion(coord, n1)) {
                for (delta2 in DXDYDZ_SLD) {
                    val n2 = coord + delta2
                    if (n2.isInBounds(R) && isVoidRegion(n1, n2)) {
                        yield(LMove(delta1, delta2) to n2)
                    }
                }
            }
        }
    }

    /** Returns true if the [from, to] region contains no Full coords. */
    fun isVoidRegion(from: Coord, to: Coord): Boolean {
        for (x in from.x..to.x) {
            for (y in from.y..to.y) {
                for (z in from.z..to.z) {
                    if (this[x, y, z]) {
                        return false
                    }
                }
            }
        }

        return true
    }

    inline fun forEach(
        from: Coord = Coord(0, 0, 0),
        to: Coord = Coord(R - 1, R - 1, R - 1),
        block: (Int, Int, Int) -> Unit
    ) {
        for (x in from.x..to.x) {
            for (y in from.y..to.y) {
                for (z in from.z..to.z) {
                    block(x, y, z)
                }
            }
        }
    }

    operator fun get(c: Coord): Boolean = get(c.x, c.y, c.z)

    operator fun get(x: Int, y: Int, z: Int): Boolean {
        val offset = x * R * R + y * R + z
        val b = coordinates[offset / 8].toInt() and 0xff
        return b and (1 shl (offset % 8)) != 0
    }

    operator fun set(c: Coord, value: Boolean) = set(c.x, c.y, c.z, value)

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
                seen[c] = true

                for (dxdydz in DXDYDZ_MLEN1) {
                    val n = c + dxdydz
                    if (n.isInBounds(R) && this[n] && !seen[n]
                        // vvv debug this later.
                        && !q.contains(n)) {
                        q.add(n)
                    }
                }
            }
        }

        // y == 0 i.e. start from the ground.
        val seen = zerosLike(this)
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

        val DXDYDZ_SLD: Array<DeltaCoord> = DXDYDZ_MLEN1.asSequence()
            .flatMap { delta -> buildSequence { for (s in 1..5) yield(delta * s) } }
            .toList()
            .toTypedArray()

        val DXDYDZ_LLD: Array<DeltaCoord> = DXDYDZ_MLEN1.asSequence()
            .flatMap { delta -> buildSequence { for (s in 1..15) yield(delta * s) } }
            .toList()
            .toTypedArray()

        init {
            check(DXDYDZ_SLD.size == 30)
            check(DXDYDZ_LLD.size == 90)
        }

        fun zerosLike(other: Matrix): Matrix {
            return other.copy(coordinates = ByteArray(other.coordinates.size))
        }
    }
}

data class Model(val matrix: Matrix) {
    val bbox: Pair<Coord, Coord> get() {
        var minCoord = Coord(matrix.R - 1, matrix.R - 1, matrix.R - 1)
        var maxCoord = Coord.ZERO
        val ord = Ordering.natural<Coord>()
        matrix.forEach(maxCoord, minCoord) { x, y, z ->
            if (matrix[x, y, z]) {
                val coord = Coord(x, y, z)
                minCoord = ord.min(minCoord, coord)
                maxCoord = ord.max(maxCoord, coord)
            }
        }

        return ord.min(minCoord, maxCoord) to ord.max(minCoord, maxCoord)
    }

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
