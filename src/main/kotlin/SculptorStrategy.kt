package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence
import kotlin.math.*

class SculptorStrategy(val model: Model) : Strategy {
    override val name: String = "sculptor"
    override val state: State = State.forModel(model)

    inner class Comb {
        val bots: ArrayList<BotView>
        private val bot1: BotView = state[1]!!

        init {
            bots = ArrayList(listOf(bot1))
        }

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

                // if we are still in the model, we should fill it on resize
                val pos = bots.last().pos + Delta(0, 0, 1)
                if (model.matrix[pos] && !state.matrix[pos]) {
                    bots.dropLast(1).forEach { b -> state.wait(b.id) }
                    state.fill(bots.last().id, Delta(0, 0, 1))
                    state.step()
                    yield(state)
                }
            }
        }
    }

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        /* Step 0 - fork bots */

        val comb = Comb()
        yieldAll(comb.resize(16))

        /* Step 1 - fill the bounding box */

        val (minCoord, maxCoord) = model.matrix.bbox()
        yieldAll(moveTo(comb.bots, minCoord))

        var xDirection = 1
        for (z in minCoord.z..maxCoord.z step comb.size) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(normalMove(comb.bots, 0, 1))
                for (x in minCoord.x..maxCoord.x) {
                    comb.bots.forEach { b -> state.fill(b.id, Delta(0, -1, 0)) }
                    state.step()
                    yield(state)
                    if (x != maxCoord.x) {
                        yieldAll(normalMove(comb.bots, xDirection, 0))
                    }
                }
                xDirection *= -1
            }

            if (z + comb.size <= maxCoord.z) {
                val shift = comb.size
                if (comb.bots.last().pos.z + comb.size >= state.matrix.R) {
                    yieldAll(comb.resize(state.matrix.R - comb.bots.last().pos.z - 1))
                }
                yieldAll(lateralMove(comb.bots, shift, Delta(xDirection, 1, 0)))
                yieldAll(normalMove(comb.bots, 0, minCoord.y - maxCoord.y - 1))
            }
        }

        /* Step 2 - sculpt the box, leaving the correctly filled model */

        yieldAll(lateralMove(comb.bots, comb.size - 16, Delta(xDirection, 1, 0)))
        yieldAll(comb.resize(16))

        val n = (maxCoord.z - minCoord.z + 1) / comb.size
        for (iz in 0..n) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(moveSculpt(comb.bots, Delta(0, -1, 0)))
                for (x in minCoord.x..maxCoord.x) {
                    yieldAll(moveSculpt(comb.bots, Delta(xDirection, 0, 0)))
                }
                xDirection *= -1
            }

            if (iz != n) {
                // TODO: remove this hack?
                for (y in 1..maxCoord.y - minCoord.y + 1) {
                    yieldAll(moveSculpt(comb.bots, Delta(0, 1, 0)))
                }
                val shift = if (comb.bots[0].pos.z - comb.size < 0) -comb.bots[0].pos.z else -comb.size
                yieldAll(moveTo(comb.bots,
                        comb.bots[0].pos + Delta(0, 0, shift),
                        Delta(xDirection, 1, 0)))
            }
        }

        /* Step 3 - tear down */

        yieldAll(comb.resize(1))

        if (comb.bots[0].pos.z > 0) {
            yieldAll(normalZMove(comb.bots, -1))
            val oldPos = comb.bots[0].pos + Delta(0, 0, 1)
            if (model.matrix[oldPos] && !state.matrix[oldPos]) {
                state.fill(comb.bots[0].id, Delta(0, 0, 1))
                state.step()
                yield(state)
            }
        }

        val delta = Coord.ZERO - comb.bots[0].pos

        yieldAll(normalMove(comb.bots, delta.dx, delta.dy))
        yieldAll(normalZMove(comb.bots, delta.dz))

        check(state.matrix == model.matrix)

        state.halt(comb.bots[0].id)
        state.step()
        yield(state)
    }

    private fun moveDestroy(bots: List<BotView>, delta: Delta): Sequence<State> = buildSequence {
        bots.forEach { b -> state.void(b.id, delta) }
        state.step()
        yield(state)
        bots.forEach { b -> state.sMove(b.id, delta) }
        state.step()
        yield(state)
    }

    private fun moveSculpt(bots: List<BotView>, delta: Delta): Sequence<State> = buildSequence {
        yieldAll(moveDestroy(bots, delta))
        bots.forEach { b -> if (model.matrix[b.pos - delta]) state.fill(b.id, -delta) else state.wait(b.id) }
        state.step()
        yield(state)
    }

    private fun moveTo(bots: List<BotView>, target: Coord, blowOutDirection: Delta = Delta(1, 1, 0)): Sequence<State> = buildSequence {
        val delta = target - bots[0].pos

        yieldAll(normalMove(bots, delta.dx, delta.dy))
        yieldAll(lateralMove(bots, delta.dz, blowOutDirection))
    }

    private fun normalMove(bots: List<BotView>, dx: Int, dy: Int): Sequence<State> = buildSequence {
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

    private fun normalZMove(bots: List<BotView>, dz: Int): Sequence<State> = buildSequence {
        var dz = dz
        while (dz != 0) {
            val shift = dz.sign * min(abs(dz), 15)
            bots.forEach { b -> state.sMove(b.id, Delta(0, 0, shift)) }
            state.step()
            yield(state)
            dz -= shift
        }
    }

    private fun lateralMove(bots: List<BotView>, dz: Int, blowOutDirection: Delta): Sequence<State> = buildSequence {
        if (dz == 0) {
            return@buildSequence
        }

        val n = sqrt(bots.count().toDouble()).toInt()
        val ny = 0 // min( n, state.matrix.R - bots[0].pos.y - 1)
        val nx = if (ny == 0) bots.count() else (bots.count() + ny - 1) / ny
        val blowOutDeltas = ArrayList<Pair<Delta, Delta>>()
        for (x in 0..nx) {
            for (y in 0..ny) {
                blowOutDeltas.add(
                        Delta(x * blowOutDirection.dx, 0, 0)
                                to Delta(0, y * blowOutDirection.dy, 0))
            }
        }

        /* Blow out */

        for (i in 0 until bots.count()) {
            when (blowOutDeltas[i].first.isLinear to blowOutDeltas[i].second.isLinear) {
                Pair(true, true) -> state.lMove(bots[i].id, blowOutDeltas[i].first, blowOutDeltas[i].second)
                Pair(true, false) -> state.sMove(bots[i].id, blowOutDeltas[i].first)
                Pair(false, true) -> state.sMove(bots[i].id, blowOutDeltas[i].second)
                Pair(false, false) -> state.wait(bots[i].id)
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
            when (blowOutDeltas[i].first.isLinear to blowOutDeltas[i].second.isLinear) {
                Pair(true, true) -> state.lMove(bots[i].id, -blowOutDeltas[i].first, -blowOutDeltas[i].second)
                Pair(true, false) -> state.sMove(bots[i].id, -blowOutDeltas[i].first)
                Pair(false, true) -> state.sMove(bots[i].id, -blowOutDeltas[i].second)
                Pair(false, false) -> state.wait(bots[i].id)
            }
        }
        state.step()
        yield(state)
    }
}
