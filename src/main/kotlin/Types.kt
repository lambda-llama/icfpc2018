package io.github.lambdallama

import com.google.common.collect.ComparisonChain
import com.google.common.math.IntMath
import java.io.File
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.abs
import kotlin.math.max


data class Coord(val x: Int, val y: Int, val z: Int) {
    fun asDelta(): Delta = Delta(x, y, z)

    fun isInBounds(matrix: Matrix) = isInBox(matrix.from to matrix.to)

    fun isInBox(bb: Pair<Coord, Coord>) =
            bb.first.x <= x && x <= bb.second.x &&
                    bb.first.y <= y && y <= bb.second.y &&
                    bb.first.z <= z && z <= bb.second.z

    operator fun plus(delta: Delta): Coord {
        return Coord(x + delta.dx, y + delta.dy, z + delta.dz)
    }

    operator fun minus(delta: Delta): Coord {
        return this + (-delta)
    }

    operator fun minus(coord: Coord): Delta {
        return Delta(x - coord.x, y - coord.y, z - coord.z)
    }

    override fun toString(): String {
        return "($x,$y,$z)"
    }

    companion object {
        val ZERO = Coord(0, 0, 0)
    }
}

data class Delta(val dx: Int, val dy: Int, val dz: Int) {
    operator fun unaryMinus() = Delta(-dx, -dy, -dz)

    operator fun plus(delta: Delta): Delta {
        return Delta(dx + delta.dx, dy + delta.dy, dz + delta.dz)
    }

    operator fun minus(delta: Delta): Delta {
        return this + (-delta)
    }

    operator fun times(s: Int) = Delta(dx * s, dy * s, dz * s)

    val mlen: Int get() = abs(dx) + abs(dy) + abs(dz)
    val clen: Int get() = max(max(abs(dx), abs(dy)), abs(dz))

    val isLinear: Boolean get() = ((dx != 0) xor (dy != 0) xor (dz != 0)) &&
            ((dx == 0) || (dy == 0) || (dz == 0))
    val isShortLinear: Boolean get() = isLinear && mlen <= 5
    val isLongLinear: Boolean get() = isLinear && mlen <= 15
    val isNear: Boolean get() = mlen in 1..2 && clen == 1
    val isFar: Boolean get() = clen in 1..30

    val isZero: Boolean get() = dx == 0 && dy == 0 && dz == 0

    override fun toString(): String {
        return "<$dx,$dy,$dz>"
    }
}

data class Matrix(
    val R: Int,
    val from: Coord,
    val to: Coord,
    val coordinates: ByteArray
) {
    fun isGrounded(): Boolean {
        val groundedMatrix = Matrix.zerosLike(this)
        val (minCoords, maxCoords) = bbox()

        val stack = ArrayList<Coord>()
        for (x in minCoords.x..maxCoords.x) {
            for (z in minCoords.z..maxCoords.z) {
                if (this[x, 0, z]) {
                    val coord = Coord(x, 0, z)
                    stack.add(coord)
                    groundedMatrix[coord] = true
                }
            }
        }

        while (stack.any()) {
            val coord = stack.removeAt(stack.size - 1)

            for (delta in DXDYDZ_MLEN1) {
                val neighbor = coord + delta

                if (neighbor.isInBounds(groundedMatrix) && !groundedMatrix[neighbor] && this[neighbor]) {
                    stack.add(neighbor)
                    groundedMatrix[neighbor] = true
                }
            }
        }

        forEach { x, y, z ->
            if (this[x, y, z] && !groundedMatrix[x, y, z]) {
                return false
            }
        }

        return true
    }

    /** All coords reachable from a given one via SMove. */
    fun sNeighborhood(coord: Coord): Sequence<Pair<SMove, Coord>> = buildSequence {
        for (dir in DXDYDZ_MLEN1) {
            var prev = coord
            for (s in 1..15) {
                val delta = dir * s
                val n = coord + delta
                if (n.isInBounds(this@Matrix) && isVoidRegion(prev, n)) {
                    yield(SMove(delta) to n)
                } else {
                    break  // No point in proceeding along this direction.
                }

                prev = n
            }
        }
    }

    /** All coords reachable from a given one via LMove. */
    fun lNeighborhood(coord: Coord): Sequence<Pair<LMove, Coord>> = buildSequence {
        for (dir1 in DXDYDZ_MLEN1) {
            var prev1 = coord
            for (s1 in 1..5) {
                val delta1 = dir1 * s1
                val n1 = coord + delta1
                if (n1.isInBounds(this@Matrix) && isVoidRegion(prev1, n1)) {
                    for (dir2 in DXDYDZ_MLEN1) {
                        var prev2 = n1
                        for (s2 in 1..5) {
                            val delta2 = dir2 * s2
                            val n2 = n1 + delta2
                            if (n2.isInBounds(this@Matrix) && isVoidRegion(prev2, n2)) {
                                yield(LMove(delta1, delta2) to n2)
                            } else {
                                break  // See comment above.
                            }

                            prev2 = n2
                        }
                    }
                } else {
                    break  // See comment above.
                }

                prev1 = n1
            }
        }
    }

    /** Returns true if the [from, to] region contains no Full coords. */
    fun isVoidRegion(from: Coord, to: Coord): Boolean {
        forEach(from, to) { x, y, z ->
            if (this[x, y, z]) {
                return false
            }
        }

        return true
    }

    inline fun forEach(
        from: Coord = this.from,
        to: Coord = this.to,
        block: (Int, Int, Int) -> Unit
    ) {
        for (x in Math.min(from.x, to.x)..Math.max(from.x, to.x)) {
            for (y in Math.min(from.y, to.y)..Math.max(from.y, to.y)) {
                for (z in Math.min(from.z, to.z)..Math.max(from.z, to.z)) {
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

    operator fun set(from: Coord, to: Coord, value: Boolean) {
        forEach(from, to) { x, y, z -> this[x, y, z] = value }
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

    fun clear() = Arrays.fill(coordinates, 0)

    fun bbox(): Pair<Coord, Coord> {
        var (minX, minY, minZ) = to
        var (maxX, maxY, maxZ) = from
        forEach { x, y, z ->
            if (this[x, y, z]) {
                minX = Math.min(minX, x)
                minY = Math.min(minY, y)
                minZ = Math.min(minZ, z)
                maxX = Math.max(maxX, x)
                maxY = Math.max(maxY, y)
                maxZ = Math.max(maxZ, z)
            }
        }

        val minCoord = Coord(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ))
        val maxCoord = Coord(Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ))
        return minCoord to maxCoord
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is Matrix -> false
            else ->
                from == other.from &&
                    to == other.to &&
                    Arrays.equals(coordinates, other.coordinates)
        }
    }

    override fun hashCode(): Int = Objects.hash(R, from, to, coordinates)

    companion object {
        val DXDYDZ_MLEN1: Array<Delta> = arrayOf(
            Delta(0, 0, 1),
            Delta(0, 1, 0),
            Delta(1, 0, 0),
            Delta(0, 0, -1),
            Delta(0, -1, 0),
            Delta(-1, 0, 0))

        val NEAR_COORD_DIFFERENCE: Array<Delta> = kotlin.run {
            val res = mutableListOf<Delta>()
            for (x in listOf(-1, 0, 1)) {
                for (y in listOf(-1, 0, 1)) {
                    for (z in listOf(-1, 0, 1)) {
                        if (x == 0 && y == 0 && z == 0) continue
                        if (x != 0 && y != 0 && z != 0) continue
                        res.add(Delta(x, y, z))
                    }
                }
            }
            res.toTypedArray()
        }

        fun zerosLike(other: Matrix): Matrix {
            return other.copy(coordinates = ByteArray(other.coordinates.size))
        }
    }
}

enum class Mode {
    Assembly,
    Disassembly,
    Reassembly,
}

data class Model(val matrix: Matrix) {
    companion object {
        fun parse(path: File): Model {
            val buf = ByteBuffer.wrap(path.readBytes())
            val R = buf.get().toInt() and 0xff
            val shape = IntMath.divide(R * R * R, 8, RoundingMode.CEILING)
            val coordinates = ByteArray(shape).apply { buf.get(this) }
            if (path.extension == "xmdl") {
                TODO()
            }

            return Model(Matrix(R, Coord.ZERO, Coord(R - 1, R - 1, R - 1), coordinates))
        }
    }
}
