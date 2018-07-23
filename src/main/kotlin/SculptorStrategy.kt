package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence
import kotlin.math.*

private suspend fun kotlin.coroutines.experimental.SequenceBuilder<State>.step(state: State) {
    state.step()
    yield(state)
}

class SculptorStrategy(val mode: Mode, val model: Model?, source: Model?) : Strategy {
    override val name: String = "sculptor"
    override val state: State = State.create(mode, model?.matrix, source?.matrix)

    private var forbiddenCoords: Set<Coord> = emptySet()

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

        var (minCoord, maxCoord) = state.targetMatrix.bbox()

        // HACK: this gfill impl doesn't support low voxel thickness
        maxCoord = Coord(
            max(maxCoord.x, minCoord.x + 2),
            max(maxCoord.y, minCoord.y + 2),
            max(maxCoord.z, minCoord.z + 2)
        )

        /* Step 1 - fill the bounding box */

        val fillerArea = Coord(
            min(Filler.MAX_FILL_SIZE, maxCoord.x - minCoord.x + 1),
            min(Filler.MAX_FILL_SIZE, maxCoord.y - minCoord.y + 1),
            min(Filler.MAX_FILL_SIZE, maxCoord.z - minCoord.z + 1)
        )
        val filler = Filler(state, minCoord to maxCoord)
        yieldAll(filler.expand())
        yieldAll(filler.resizeToFit(fillerArea))
        yieldAll(filler.move(minCoord + Delta(-1, 0, 0) - filler.pos))

        filler.digMode = true

        // We will be stepping two less than box size to avoid leaving ungrounded move traces

        val correctingLength = 2
        val xSteps =
                ((maxCoord.x - minCoord.x + 1) - correctingLength).toFloat() /
                        (filler.boxSize.x - correctingLength)
        val ySteps =
                ((maxCoord.y - minCoord.y + 1) - correctingLength).toFloat() /
                        (filler.boxSize.y - correctingLength)
        val zSteps =
                ((maxCoord.z - minCoord.z + 1) - correctingLength).toFloat() /
                        (filler.boxSize.z - correctingLength)
        var yDirection = 1
        var zDirection = 1
        for (x in 1..ceil(xSteps).toInt()) {
            for (y in 1..ceil(ySteps).toInt()) {
                for (z in 1..ceil(zSteps).toInt()) {
                    yieldAll(filler.fillBox())
                    val shift = min(filler.boxSize.z - correctingLength, if (zDirection > 0)
                        (maxCoord.z - filler.boxMaxCoord.z) else
                        (filler.boxMinCoord.z - minCoord.z))
                    yieldAll(filler.move(Delta(0, 0, zDirection * shift)))
                }
                yieldAll(filler.fillBox())
                val shift = min(filler.boxSize.y - correctingLength, if (yDirection > 0)
                    (maxCoord.y - filler.boxMaxCoord.y) else
                    (filler.boxMinCoord.y - minCoord.y))
                yieldAll(filler.move(Delta(0, yDirection * shift, 0)))
                zDirection *= -1
            }
            val shift = min(filler.boxSize.x - correctingLength, maxCoord.x - filler.boxMaxCoord.x)
            yieldAll(filler.move(Delta(shift, 0, 0)))
            yDirection *= -1
        }

        yieldAll(filler.collapse())

        // TODO: find closest exit
        yieldAll(filler.digZMove(filler.bots, minCoord.z - filler.pos.z - 1))

        filler.digMode = false

        val maxCombSize = min(40, maxCoord.z - minCoord.z + 1)
        val comb = Comb()
        yieldAll(comb.moveTo(Coord(comb.pos.x, maxCoord.y + 1, comb.pos.z)))
        yieldAll(comb.moveTo(Coord(minCoord.x, maxCoord.y + 1, minCoord.z)))
        yieldAll(comb.resize(maxCombSize))

        state.matrix.forEach { x, y, z ->
            val coord = Coord(x, y, z)
            check(coord.isInBox(minCoord to maxCoord) == state.matrix[x, y, z])
        }
        check(comb.pos == Coord(minCoord.x, maxCoord.y + 1, minCoord.z))

        /* Step 2 - sculpt the box, leaving the correctly filled targetMatrix */

        forbiddenCoords = getForbiddenCoordinates(state.targetMatrix)

        var xDirection = 1
        for (z in minCoord.z .. maxCoord.z step comb.size) {
            while (comb.pos.y > 0) {
                yieldAll(comb.moveSculpt(Delta(0, -1, 0)))
                for (x in minCoord.x .. maxCoord.x - 1) {
                    yieldAll(comb.moveSculpt(Delta(xDirection, 0, 0)))
                }
                xDirection *= -1
            }

            if (comb.pos.z < maxCoord.z - comb.size + 1) {
                yieldAll(comb.moveSculpt(Delta(-xDirection, 0, 0)))
                yieldAll(comb.normalMove(0, maxCoord.y - comb.pos.y + 1))
                yieldAll(comb.normalMove(xDirection, 0))
                val oldSize = comb.size
                val newSize = min(maxCoord.z - comb.pos.z - comb.size + 1, comb.size)
                yieldAll(comb.resize(newSize))
                yieldAll(comb.move(Delta(0, 0, oldSize), Delta(xDirection, 1, 0)))
            }
        }

        /* Step 3 - tear down */

        yieldAll(comb.resize(1))

        yieldAll(comb.moveSculpt(Delta(-xDirection, 0, 0)))
        yieldAll(comb.normalZMove(-comb.pos.z))
        yieldAll(comb.normalMove(-comb.pos.x, 0))

        check(state.matrix == state.targetMatrix)

        state.halt(comb.bots[0].id)
        step(state)
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
                    step(state)
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
                step(state)
            }
        }

        yieldAll(comb.moveTo(Coord.ZERO))

        check(state.matrix == state.targetMatrix)

        state.halt(comb.bots[0].id)
        step(state)
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
                step(state)
            }
            while (size > newSize) {
                bots.dropLast(2).forEach { b -> state.wait(b.id) }
                state.fusion(bots.dropLast(1).last().id, bots.last().id)
                bots.removeAt(size - 1)
                step(state)

                // if we are in the targetMatrix, we should fill it on resize
                val pos = bots.last().pos + Delta(0, 0, 1)
                if (state.targetMatrix[pos] && !state.matrix[pos]) {
                    bots.dropLast(1).forEach { b -> state.wait(b.id) }
                    state.fill(bots.last().id, Delta(0, 0, 1))
                    step(state)
                }
            }
        }

        fun halt(): Sequence<State> = buildSequence {
            state.halt(bots[0].id)
            step(state)
        }

        fun fill(delta: Delta): Sequence<State> = buildSequence {
            if (bots.all { b -> state.matrix[b.pos + delta] }) {
                return@buildSequence
            }

            bots.forEach { b -> if (state.matrix[b.pos + delta]) state.wait(b.id) else state.fill(b.id, delta) }
            step(state)
        }

        fun void(delta: Delta): Sequence<State> = buildSequence {
            if (bots.all { b -> !state.matrix[b.pos + delta] }) {
                return@buildSequence
            }

            bots.forEach { b -> if (!state.matrix[b.pos + delta]) state.wait(b.id) else state.void(b.id, delta) }
            step(state)
        }

        private fun flipHarmonics(): Sequence<State> = buildSequence {
            state.flip(bots[0].id)
            bots.drop(1).forEach { state.wait(it.id) }
            step(state)
        }

        private fun switchToHighIfNeeded(willBeVoided: List<Coord>) = buildSequence {
            if (state.harmonics == Harmonics.Low && willBeVoided.any { it in forbiddenCoords  }) {
                yieldAll(flipHarmonics())
            }
        }

        fun switchToLowIfNeeded() = buildSequence {
            if (state.harmonics == Harmonics.High && forbiddenCoords.all { state.matrix[it] }) {
                yieldAll(flipHarmonics())
            }
        }

        fun moveSculpt(delta: Delta): Sequence<State> = buildSequence {
            if (delta.isZero) {
                return@buildSequence
            }
            yieldAll(switchToHighIfNeeded(bots.map { it.pos + delta }))
            yieldAll(void(delta))

            bots.forEach { b -> state.sMove(b.id, delta) }
            step(state)

            if (bots.any { b -> state.targetMatrix[b.pos - delta] }) {
                bots.forEach { b -> if (state.targetMatrix[b.pos - delta]) state.fill(b.id, -delta) else state.wait(b.id) }
                step(state)
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
                step(state)
                dx -= shift
            }

            var dy = dy
            while (dy != 0) {
                val shift = dy.sign * min(abs(dy), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, shift, 0)) }
                step(state)
                dy -= shift
            }
        }

        fun erasingMove(delta: Delta): Sequence<State> = buildSequence {
            require(delta.mlen == 1)
            if (bots.any { b -> state.matrix[b.pos + delta] }) {
                yieldAll(switchToHighIfNeeded(bots.map { it.pos + delta }))
                bots.forEach { b -> if (state.matrix[b.pos + delta]) state.void(b.id, delta) else state.wait(b.id) }
                step(state)
            }
            bots.forEach { b -> state.sMove(b.id, delta) }
            step(state)
        }

        fun normalZMove(dz: Int): Sequence<State> = buildSequence {
            var dz = dz
            while (dz != 0) {
                val shift = dz.sign * min(abs(dz), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
                step(state)
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
            step(state)

            /* Move */

            var dz = dz
            while (dz != 0) {
                val shift = dz.sign * min(abs(dz), 15)
                bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
                step(state)
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
            step(state)
        }
    }
}

abstract class BotGroup(protected val state: State) {
    fun normalXMove(bots: List<BotView>, dx: Int, maxX: Int = 15): Sequence<State> = buildSequence {
        var dx = dx
        while (dx != 0) {
            val shift = dx.sign * min(abs(dx), maxX)
            bots.forEach { b -> state.sMove(b.id, Delta(shift, 0, 0)) }
            step(state)
            dx -= shift
        }
    }

    fun normalYMove(bots: List<BotView>, dy: Int, maxY: Int = 15): Sequence<State> = buildSequence {
        var dy = dy
        while (dy != 0) {
            val shift = dy.sign * min(abs(dy), maxY)
            bots.forEach { b -> state.sMove(b.id, Delta(0, shift, 0)) }
            step(state)
            dy -= shift
        }
    }

    fun normalZMove(bots: List<BotView>, dz: Int, maxZ: Int = 15): Sequence<State> = buildSequence {
        var dz = dz
        while (dz != 0) {
            val shift = dz.sign * min(abs(dz), maxZ)
            bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
            step(state)
            dz -= shift
        }
    }
}

class Filler(state: State, val bbox: Pair<Coord, Coord>) : BotGroup(state) {
    companion object {
        const val MAX_FILL_SIZE = 30
    }

    val bots: ArrayList<BotView> = ArrayList(listOf(state[1]!!))
    var top: List<BotView> = listOf()
    var bottom: List<BotView> = listOf()
    var left: List<BotView> = listOf()
    var right: List<BotView> = listOf()
    var front: List<BotView> = listOf()
    var back: List<BotView> = listOf()

    val pos: Coord get() = bots[0].pos
    val boxMinCoord: Coord get() = pos + Delta(1, 0, 0)
    val boxMaxCoord: Coord get() = boxMinCoord + boxSize.asDelta() - Delta(1, 1, 1)
    var size: Coord = Coord(1, 1, 1)
        private set
    val boxSize: Coord get() = Coord(size.x - 2, size.y, size.z)

    var digMode: Boolean = false

    fun expand(): Sequence<State> = buildSequence {
        bots.add(state[state.fission(bots[0].id, Delta(0, 0, 1), 3)]!!)
        step(state)

        bots.addAll(bots.map { b -> state.fission(b.id, Delta(0, 1, 0), 1) }.map { i -> state[i]!! })
        step(state)

        bots.addAll(bots.map { b -> state.fission(b.id, Delta(1, 0, 0), 0) }.map { i -> state[i]!! })
        step(state)

        top = listOf(2, 3, 6, 7).map { i -> bots[i] }.toList()
        bottom = listOf(0, 1, 4, 5).map { i -> bots[i] }.toList()
        left = listOf(0, 2, 4, 6).map { i -> bots[i] }.toList()
        right = listOf(1, 3, 5, 7).map { i -> bots[i] }.toList()
        front = listOf(0, 1, 2, 3).map { i -> bots[i] }.toList()
        back = listOf(4, 5, 6, 7).map { i -> bots[i] }.toList()

        size = Coord(2, 2, 2)
    }

    fun fillBox(): Sequence<State> = buildSequence {
        state.gFillBox(
                bots[0].id, Delta(1, 0, 0),
                bots[1].id, Delta(1, 0, 0),
                bots[2].id, Delta(1, 0, 0),
                bots[3].id, Delta(1, 0, 0),
                bots[4].id, Delta(-1, 0, 0),
                bots[5].id, Delta(-1, 0, 0),
                bots[6].id, Delta(-1, 0, 0),
                bots[7].id, Delta(-1, 0, 0))
        step(state)
    }

    fun collapse(): Sequence<State> = buildSequence {
        yieldAll(resize(Coord(2, 2, 2), dig = true))

        for ((f, b) in front.zip(back)) {
            state.fusion(f.id, b.id)
        }
        step(state)

        yieldAll(fill(front, Delta(1, 0, 0)))

        state.fusion(bots[0].id, bots[2].id)
        state.fusion(bots[1].id, bots[3].id)
        step(state)

        yieldAll(fill(listOf(bots[0], bots[1]), Delta(0, 1, 0)))

        state.fusion(bots[0].id, bots[1].id)
        step(state)

        yieldAll(fill(listOf(bots[0]), Delta(0, 0, 1)))

        while (bots.count() > 1) {
            bots.removeAt(1)
        }

        top = listOf()
        bottom = listOf()
        left = listOf()
        right = listOf()
        front = listOf()
        back = listOf()

        size = Coord(1, 1, 1)
    }

    fun move(delta: Delta): Sequence<State> = buildSequence {
        if (digMode) {
            yieldAll(digXMove(bots, delta.dx))
            yieldAll(digYMove(bots, delta.dy))
            yieldAll(digZMove(bots, delta.dz))
        } else {
            yieldAll(normalXMove(bots, delta.dx, maxX = min(back[0].pos.x - front[0].pos.x - 1, 15)))
            yieldAll(normalYMove(bots, delta.dy, maxY = min(top[0].pos.y - bottom[0].pos.y - 1, 15)))
            yieldAll(normalZMove(bots, delta.dz, maxZ = min(right[0].pos.z - left[0].pos.z - 1, 15)))
        }
    }

    fun digXMove(bots: List<BotView>, dx: Int): Sequence<State> = buildSequence {
        for (x in 1..abs(dx)) {
            yieldAll(void(bots, Delta(dx.sign, 0, 0)))
            yieldAll(normalXMove(bots, dx.sign))
            yieldAll(fill(bots, Delta(-dx.sign, 0, 0)))
        }
    }

    fun digYMove(bots: List<BotView>, dy: Int): Sequence<State> = buildSequence {
        for (y in 1..abs(dy)) {
            yieldAll(void(bots, Delta(0, dy.sign, 0)))
            yieldAll(normalYMove(bots, dy.sign))
            yieldAll(fill(bots, Delta(0, -dy.sign, 0)))
        }
    }

    fun digZMove(bots: List<BotView>, dz: Int): Sequence<State> = buildSequence {
        for (z in 1..abs(dz)) {
            yieldAll(void(bots, Delta(0, 0, dz.sign)))
            yieldAll(normalZMove(bots, dz.sign))
            yieldAll(fill(bots, Delta(0, 0, -dz.sign)))
        }
    }

    fun fill(bots: List<BotView>, delta: Delta): Sequence<State> = buildSequence {
        val coords = bots.map { b -> b.pos + delta }
        if (coords.all { c -> state.matrix[c] || !c.isInBox(bbox) }) {
            return@buildSequence
        }

        bots.zip(coords).forEach { (b, c) ->
            if (state.matrix[c] || !c.isInBox(bbox)) state.wait(b.id) else state.fill(b.id, delta) }
        step(state)
    }

    fun void(bots: List<BotView>, delta: Delta): Sequence<State> = buildSequence {
        if (bots.all { b -> !state.matrix[b.pos + delta] }) {
            return@buildSequence
        }

        bots.forEach { b -> if (!state.matrix[b.pos + delta]) state.wait(b.id) else state.void(b.id, delta) }
        step(state)
    }

    fun resizeToFit(area: Coord): Sequence<State> = buildSequence {
        yieldAll(resize(Coord(area.x + 2, area.y, area.z)))
    }

    fun resize(newSize: Coord, dig: Boolean = false): Sequence<State> = buildSequence {
        yieldAll(resizeX(newSize.x, dig))
        yieldAll(resizeY(newSize.y, dig))
        yieldAll(resizeZ(newSize.z, dig))
    }

    private fun resizeX(targetX: Int, dig: Boolean): Sequence<State> = buildSequence {
        val origin = pos - Delta(1, 1, 1)
        while (size.x != targetX) {
            val delta = back[0].pos - origin
            val dx = targetX - delta.dx
            val shift = dx.sign * min(abs(dx), 15)

            if (shift != 0) {
                front.forEach { b -> state.wait(b.id) }
                if (dig) {
                    yieldAll(digXMove(back, shift))
                } else {
                    for (bot in back) {
                        state.sMove(bot.id, Delta(shift, 0, 0))
                    }
                    step(state)
                }
                size += Delta(shift, 0, 0)
            }
        }
    }

    private fun resizeY(targetY: Int, dig: Boolean): Sequence<State> = buildSequence {
        val origin = pos - Delta(1, 1, 1)
        while (size.y != targetY) {
            val delta = top[0].pos - origin
            val dy = targetY - delta.dy
            val shift = dy.sign * min(abs(dy), 15)

            if (shift != 0) {
                bottom.forEach { b -> state.wait(b.id) }
                if (dig) {
                    yieldAll(digYMove(top, shift))
                } else {
                    for (bot in top) {
                        state.sMove(bot.id, Delta(0, shift, 0))
                    }
                    step(state)
                }
                size += Delta(0, shift, 0)
            }
        }
    }

    private fun resizeZ(targetZ: Int, dig: Boolean): Sequence<State> = buildSequence {
        val origin = pos - Delta(1, 1, 1)
        while (size.z != targetZ) {
            val delta = right[0].pos - origin
            val dz = targetZ - delta.dz
            val shift = dz.sign * min(abs(dz), 15)

            if (shift != 0) {
                left.forEach { b -> state.wait(b.id) }
                if (dig) {
                    yieldAll(digZMove(right, shift))
                } else {
                    for (bot in right) {
                        state.sMove(bot.id, Delta(0, 0, shift))
                    }
                    step(state)
                }
                size += Delta(0, 0, shift)
            }
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
