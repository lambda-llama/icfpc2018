package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence
import kotlin.math.*

class SculptorStrategy(val mode: Mode, val model: Model?, source: Model?) : Strategy {
    override val name: String = "sculptor"
    override val state: State = State.create(mode, model?.matrix, source?.matrix)

    private var forbiddenCoords: Set<Coord> = emptySet()
    private val cBeams = emptyList<Beam>()

    override fun run(): Sequence<State> {
        return when (mode) {
            Mode.Assembly -> runAssembly()
            Mode.Disassembly -> runDisassembly()
            Mode.Reassembly -> runReassembly()
        }
    }

    private fun runAssembly(): Sequence<State> = buildSequence {
        yield(state)

        /* Step 0 - fork bots */

        val (minCoord, maxCoord) = state.targetMatrix.bbox()
        val maxCombSize = min(40, maxCoord.z - minCoord.z + 1)

        val comb = Comb()
        yieldAll(comb.moveTo(minCoord))
        yieldAll(comb.resize(maxCombSize))

        /* Step 1 - fill the bounding box */

        var xDirection = 1
        var totalZShifts = 1
        for (z in minCoord.z..maxCoord.z step comb.size) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(comb.normalMove(0, 1))
                for (x in minCoord.x..maxCoord.x) {
                    comb.bots.forEach { b -> state.fill(b.id, Delta(0, -1, 0)) }
                    state.step()
                    yield(state)
                    if (x != maxCoord.x) {
                        yieldAll(comb.normalMove(xDirection, 0))
                    }
                }
                xDirection *= -1
            }

            if (z + comb.size <= maxCoord.z) {
                val shift = comb.size
                if (comb.bots.last().pos.z + comb.size >= state.matrix.R) {
                    yieldAll(comb.resize(state.matrix.R - comb.bots.last().pos.z - 1))
                }
                yieldAll(comb.lateralMove(shift, Delta(xDirection, 1, 0)))
                yieldAll(comb.normalMove(0, minCoord.y - maxCoord.y - 1))

                totalZShifts += 1
            }
        }

        /* Step 2 - sculpt the box, leaving the correctly filled targetMatrix */
        forbiddenCoords = getForbiddenCoordinates(state.targetMatrix)
        yieldAll(comb.lateralMove(comb.size - maxCombSize, Delta(xDirection, 1, 0)))
        yieldAll(comb.resize(maxCombSize))

        for (iz in 1..totalZShifts) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(comb.moveSculpt(Delta(0, -1, 0)))
                for (x in minCoord.x..maxCoord.x) {
                    yieldAll(comb.moveSculpt(Delta(xDirection, 0, 0)))
                }
                xDirection *= -1
            }

            if (comb.bots[0].pos.z > minCoord.z) {
                // TODO: remove this hack?
                for (y in 1..maxCoord.y - minCoord.y + 1) {
                    yieldAll(comb.moveSculpt(Delta(0, 1, 0)))
                }
                val shift = if (comb.bots[0].pos.z - comb.size < 0) -comb.bots[0].pos.z else -comb.size
                yieldAll(comb.move(Delta(0, 0, shift), Delta(xDirection, 1, 0)))
            }
        }

        /* Step 3 - tear down */

        yieldAll(comb.resize(1))

        if (comb.bots[0].pos.z > 0) {
            yieldAll(comb.normalZMove(-1))
            val oldPos = comb.bots[0].pos + Delta(0, 0, 1)
            if (state.targetMatrix[oldPos] && !state.matrix[oldPos]) {
                state.fill(comb.bots[0].id, Delta(0, 0, 1))
                state.step()
                yield(state)
            }
        }

        yieldAll(comb.moveTo(Coord.ZERO))

        check(state.matrix == state.targetMatrix)

        state.halt(comb.bots[0].id)
        state.step()
        yield(state)
    }

    private fun runReassembly(): Sequence<State> = buildSequence {
        forbiddenCoords = getForbiddenCoordinates(state.matrix)
        yield(state)

        /* Step 0 - fork bots */

        val (minCoord, maxCoord) = bbUnion(
                state.matrix.bbox(),
                state.targetMatrix.bbox()
        )
        val maxCombSize = min(40, maxCoord.z - minCoord.z + 1)

        val comb = Comb()
        yieldAll(comb.moveTo(minCoord.copy(x = minCoord.x - 1)))
        yieldAll(comb.resize(maxCombSize))

        /* Step 1 - fill the bounding box, erasing old stuff */

        var xDirection = 1
        var totalZShifts = 1
        for (z in minCoord.z..maxCoord.z step comb.size) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(comb.erasingMove(Delta(xDirection, 0, 0)))
                for (x in minCoord.x..maxCoord.x) {
                    yield(state)
//                    if (x != maxCoord.x) {
                    yieldAll(comb.erasingMove(Delta(xDirection, 0, 0)))
//                    }
                    comb.bots.forEach { b -> state.fill(b.id, Delta(-xDirection, 0, 0)) }
                    state.step()
                    yieldAll(comb.switchToLowIfNeeded())
                }
                xDirection *= -1
                yieldAll(comb.normalMove(0, 1))
            }

            if (z + comb.size <= maxCoord.z) {
                val shift = comb.size
                if (comb.bots.last().pos.z + comb.size >= state.matrix.R) {
                    yieldAll(comb.resize(state.matrix.R - comb.bots.last().pos.z - 1))
                }
                yieldAll(comb.lateralMove(shift, Delta(xDirection, 1, 0)))
                yieldAll(comb.normalMove(0, minCoord.y - maxCoord.y - 1))

                totalZShifts += 1
            }
        }

        /* Step 2 - sculpt the box, leaving the correctly filled targetMatrix */
        forbiddenCoords = getForbiddenCoordinates(state.targetMatrix)
        yieldAll(comb.lateralMove(comb.size - maxCombSize, Delta(xDirection, 1, 0)))
        yieldAll(comb.resize(maxCombSize))

        for (iz in 1..totalZShifts) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(comb.moveSculpt(Delta(0, -1, 0)))
                for (x in minCoord.x..maxCoord.x) {
                    yieldAll(comb.moveSculpt(Delta(xDirection, 0, 0)))
                }
                xDirection *= -1
            }

            if (comb.bots[0].pos.z > minCoord.z) {
                // TODO: remove this hack?
                for (y in 1..maxCoord.y - minCoord.y + 1) {
                    yieldAll(comb.moveSculpt(Delta(0, 1, 0)))
                }
                val shift = if (comb.bots[0].pos.z - comb.size < 0) -comb.bots[0].pos.z else -comb.size
                yieldAll(comb.move(Delta(0, 0, shift), Delta(xDirection, 1, 0)))
            }
        }

        /* Step 3 - tear down */

        yieldAll(comb.resize(1))

        if (comb.bots[0].pos.z > 0) {
            yieldAll(comb.normalZMove(-1))
            val oldPos = comb.bots[0].pos + Delta(0, 0, 1)
            if (state.targetMatrix[oldPos] && !state.matrix[oldPos]) {
                state.fill(comb.bots[0].id, Delta(0, 0, 1))
                state.step()
                yield(state)
            }
        }

        yieldAll(comb.moveTo(Coord.ZERO))

        check(state.matrix == state.targetMatrix)

        state.halt(comb.bots[0].id)
        state.step()
        yield(state)
    }

    private fun runDisassembly(): Sequence<State> = buildSequence {
        yield(state)

        /* Step 0 - fork bots */

        val (minCoord, maxCoord) = state.matrix.bbox()
        val maxCombSize = min(40, maxCoord.z - minCoord.z + 1)

        val comb = Comb()
        yieldAll(comb.moveTo(Coord(maxCoord.x + 1, 0, minCoord.z - 1)))
        yieldAll(comb.move(Delta(0, 0, 1)))
        yieldAll(comb.resize(maxCombSize))

        /* Step 1 - build far wall */

        while (comb.pos.y < maxCoord.y) {
            yieldAll(comb.move(Delta(0, 1, 0)))
            yieldAll(comb.fill(Delta(0, -1, 0)))
        }
        yieldAll(comb.move(Delta(0, 1, 0)))

        /* Step 2 - build near wall */

        yieldAll(comb.moveTo(Coord(minCoord.x - 1, comb.pos.y, minCoord.z)))
        yieldAll(comb.moveTo(Coord(minCoord.x - 1, minCoord.y, minCoord.z)))

        while (comb.pos.y < maxCoord.y) {
            yieldAll(comb.move(Delta(0, 1, 0)))
            yieldAll(comb.fill(Delta(0, -1, 0)))
        }

        /* Step 3 - disassemble */

        var xDirection = 1
        while (comb.pos.y > minCoord.y) {
            for (x in minCoord.x - 1..maxCoord.x) {
                yieldAll(comb.fill(Delta(xDirection, -1, 0)))
                yieldAll(comb.void(Delta(xDirection, 0, 0)))
                yieldAll(comb.move(Delta(xDirection, 0, 0)))
            }
            yieldAll(comb.void(Delta(0, -1, 0)))
            yieldAll(comb.move(Delta(0, -1, 0)))
            xDirection *= -1
        }

        for (x in minCoord.x - 1..maxCoord.x) {
            yieldAll(comb.void(Delta(xDirection, 0, 0)))
            yieldAll(comb.move(Delta(xDirection, 0, 0)))
        }

        /* Step 4 - tear down */

        yieldAll(comb.resize(1))
        yieldAll(comb.moveTo(Coord.ZERO))

        check(state.matrix == state.targetMatrix)

        yieldAll(comb.halt())
    }

    inner class Comb {
        val bots: ArrayList<BotView> = ArrayList(listOf(state[1]!!))

        val pos: Coord get() = bots[0].pos
        val size: Int get() = bots.count()

        fun resize(newSize: Int): Sequence<State> = buildSequence {
            while (size < newSize) {
                bots.dropLast(1).forEach { b -> state.wait(b.id) }
                val id = state.fission(bots.last().id, Delta(0, 0, 1), bots.last().seeds().count() - 1)
                bots.add(state[id]!!)
                state.step()
                yield(state)
            }
            while (size > newSize) {
                bots.dropLast(2).forEach { b -> state.wait(b.id) }
                state.fusion(bots.dropLast(1).last().id, bots.last().id)
                bots.removeAt(size - 1)
                state.step()
                yield(state)

                // if we are in the targetMatrix, we should fill it on resize
                val pos = bots.last().pos + Delta(0, 0, 1)
                if (state.targetMatrix[pos] && !state.matrix[pos]) {
                    bots.dropLast(1).forEach { b -> state.wait(b.id) }
                    state.fill(bots.last().id, Delta(0, 0, 1))
                    state.step()
                    yield(state)
                }
            }
        }

        fun halt(): Sequence<State> = buildSequence {
            state.halt(bots[0].id)
            state.step()
            yield(state)
        }

        fun fill(delta: Delta): Sequence<State> = buildSequence {
            if (bots.all { b -> state.matrix[b.pos + delta] }) {
                return@buildSequence
            }

            bots.forEach { b -> if (state.matrix[b.pos + delta]) state.wait(b.id) else state.fill(b.id, delta) }
            state.step()
            yield(state)
        }

        fun void(delta: Delta): Sequence<State> = buildSequence {
            if (bots.all { b -> !state.matrix[b.pos + delta] }) {
                return@buildSequence
            }

            bots.forEach { b -> if (!state.matrix[b.pos + delta]) state.wait(b.id) else state.void(b.id, delta) }
            state.step()
            yield(state)
        }

        private fun flipHarmonics() {
            state.flip(bots[0].id)
            bots.drop(1).forEach { state.wait(it.id) }
            state.step()
        }

        private fun switchToHighIfNeeded(willBeVoided: List<Coord>) = buildSequence {
            if (state.harmonics == Harmonics.Low && willBeVoided.any { it in forbiddenCoords  }) {
                flipHarmonics()
                yield(state)
            }
        }

        fun switchToLowIfNeeded() = buildSequence {
            if (state.harmonics == Harmonics.High && forbiddenCoords.all { state.matrix[it] }) {
                flipHarmonics()
                yield(state)
            }
        }

        fun moveSculpt(delta: Delta): Sequence<State> = buildSequence {
            yieldAll(switchToHighIfNeeded(bots.map { it.pos + delta }))
            bots.forEach { b -> state.void(b.id, delta) }
            state.step()
            yield(state)

            bots.forEach { b -> state.sMove(b.id, delta) }
            state.step()
            yield(state)

            if (bots.any { b -> state.targetMatrix[b.pos - delta] }) {
                bots.forEach { b -> if (state.targetMatrix[b.pos - delta]) state.fill(b.id, -delta) else state.wait(b.id) }
                state.step()
                yield(state)
                yieldAll(switchToLowIfNeeded())
            }
        }

        fun moveTo(target: Coord, blowOutDirection: Delta = Delta(1, 1, 0)): Sequence<State> {
            return move(target - bots[0].pos, blowOutDirection)
        }

        fun move(delta: Delta, blowOutDirection: Delta = Delta(1, 1, 0)): Sequence<State> = buildSequence {
            yieldAll(normalMove(delta.dx, delta.dy))
            if (size > 1) {
                yieldAll(lateralMove(delta.dz, blowOutDirection))
            } else {
                yieldAll(normalZMove(delta.dz))
            }
        }

        fun normalMove(dx: Int, dy: Int): Sequence<State> = buildSequence {
            var dx = dx
            while (dx != 0) {
                val shift = dx.sign * min(abs(dx), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(shift, 0, 0)) }
                state.step()
                yield(state)
                dx -= shift
            }

            var dy = dy
            while (dy != 0) {
                val shift = dy.sign * min(abs(dy), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, shift, 0)) }
                state.step()
                yield(state)
                dy -= shift
            }
        }

        fun erasingMove(delta: Delta): Sequence<State> = buildSequence {
            require(delta.mlen == 1)
            if (bots.any { b -> state.matrix[b.pos + delta] }) {
                yieldAll(switchToHighIfNeeded(bots.map { it.pos + delta }))
                bots.forEach { b -> if (state.matrix[b.pos + delta]) state.void(b.id, delta) else state.wait(b.id) }
                state.step()
                yield(state)
            }
            bots.forEach { b -> state.sMove(b.id, delta) }
            state.step()
            yield(state)
        }

        fun normalZMove(dz: Int): Sequence<State> = buildSequence {
            var dz = dz
            while (dz != 0) {
                val shift = dz.sign * min(abs(dz), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
                state.step()
                yield(state)
                dz -= shift
            }
        }

        fun lateralMove(dz: Int, blowOutDirection: Delta): Sequence<State> = buildSequence {
            if (dz == 0) {
                return@buildSequence
            }

            val blowOutDeltas = ArrayList<Delta>()
            for (x in 0..16) {
                blowOutDeltas.add(Delta(x * blowOutDirection.dx, 0, 0))
            }

            /* Blow out */

            for (i in 0 until bots.count()) {
                val batchIdx = i % 16
                if (batchIdx == 0) {
                    state.wait(bots[i].id)
                } else {
                    state.sMove(bots[i].id, blowOutDeltas[batchIdx])
                }
            }
            state.step()
            yield(state)

            /* Move */

            var dz = dz
            while (dz != 0) {
                val shift = dz.sign * min(abs(dz), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
                state.step()
                yield(state)
                dz -= shift
            }

            /* Blow in */

            for (i in 0 until bots.count()) {
                val batchIdx = i % 16
                if (batchIdx == 0) {
                    state.wait(bots[i].id)
                } else {
                    state.sMove(bots[i].id, -blowOutDeltas[batchIdx])
                }
            }
            state.step()
            yield(state)
        }
    }
}


data class Beam(val x: Int, val y: Int) {
    fun contains(c: Coord): Boolean =
            (c.x == x || c.x == x - 1 || c.x == x + 1) && c.y == y
}

enum class WalkState {
    InProgress,
    HasLowerGround,
    LowerGroundUnreachable

}

fun getForbiddenCoordinates(m: Matrix): Set<Coord> {
    return criticalBeams(m).flatMap { beam ->
        (1..m.R).map { z ->
            Coord(beam.x, beam.y, z)
        }
    }.filter { it.isInBounds(m) && m[it] }
            .toSet()
}

/**
 * Compute the beams cutting through which risks making some voxels ungrounded
 */
fun criticalBeams(m: Matrix): Set<Beam> {
    val bb = m.bbox()
    fun isCritical(b: Beam): Boolean {
        val cache = HashSet<Coord>()
        fun hasLowerGround(c: Coord): Boolean {
            val work = mutableSetOf(c)
            val done = mutableSetOf<Coord>()
            while (work.isNotEmpty()) {
                val c = work.minBy { it.y }!!
                work.remove(c)
                done.add(c)
                if (b.contains(c) || !c.isInBounds(m) || !m[c]) continue
                if (c in cache || c.y <= b.y) {
                    done.forEach { cache.add(it) }
                    work.forEach { cache.add(it) }
                    return true
                }
                Matrix.DXDYDZ_MLEN1
                        .map { c + it }
                        .filter { it !in work && it !in done }
                        .forEach { work.add(it) }
            }
            return false
        }

        val coordsAbove = (bb.first.z..bb.second.z)
                .map { z -> Coord(b.x, b.y + 1, z) }
                .filter { it.isInBounds(m) && m[it] }
        return coordsAbove.any { !hasLowerGround(it) }
    }

    val beams = (bb.first.x..bb.second.x).flatMap { x ->
        (0..bb.second.y).map { y -> Beam(x, y) }
    }

    return beams.filter { isCritical(it) }.toSet()
}


fun bbUnion(bb1: Pair<Coord, Coord>, bb2: Pair<Coord, Coord>): Pair<Coord, Coord> =
        Coord(
                min(bb1.first.x, bb2.first.x),
                min(bb1.first.y, bb2.first.y),
                min(bb1.first.z, bb2.first.z)
        ) to Coord(
                max(bb1.second.x, bb2.second.x),
                max(bb1.second.y, bb2.second.y),
                max(bb1.second.z, bb2.second.z)
        )
