package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence
import kotlin.math.*

class SculptorStrategy(val model: Model) : Strategy {
    override val name: String = "sculptor"
    override val state: State = State.forModel(model)

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        /* Step 0 - fork bots */

        var bot1 = state[1]!!
        var bot2 = state[state.fission(bot1.id, Delta(1, 0, 1), bot1.seeds().count() / 2)]!!
        state.step()
        yield(state)

        state.sMove(bot1.id, Delta(1, 0, 0))
        state.sMove(bot2.id, Delta(0, 0, 1))
        state.step()
        yield(state)

        var bot3 = state[state.fission(bot1.id, Delta(1, 0, 1), bot1.seeds().count() / 2)]!!
        var bot4 = state[state.fission(bot2.id, Delta(1, 0, 1), bot2.seeds().count() / 2)]!!
        state.step()
        yield(state)

        state.sMove(bot1.id, Delta(1, 0, 0))
        state.lMove(bot2.id, Delta(0, 0, 4), Delta(1, 0, 0))
        state.sMove(bot3.id, Delta(0, 0, 1))
        state.sMove(bot4.id, Delta(0, 0, 1))
        state.step()
        yield(state)

        var bot5 = state[state.fission(bot1.id, Delta(1, 0, 1), bot1.seeds().count() / 2)]!!
        var bot6 = state[state.fission(bot2.id, Delta(1, 0, 1), bot2.seeds().count() / 2)]!!
        var bot7 = state[state.fission(bot3.id, Delta(1, 0, 1), bot3.seeds().count() / 2)]!!
        var bot8 = state[state.fission(bot4.id, Delta(1, 0, 1), bot4.seeds().count() / 2)]!!
        state.step()
        yield(state)

        state.sMove(bot1.id, Delta(1, 0, 0))
        state.sMove(bot2.id, Delta(1, 0, 0))
        state.sMove(bot3.id, Delta(1, 0, 0))
        state.sMove(bot4.id, Delta(1, 0, 0))
        state.wait(bot5.id)
        state.wait(bot6.id)
        state.wait(bot7.id)
        state.wait(bot8.id)
        state.step()
        yield(state)

        val bots = listOf(bot1, bot2, bot3, bot4, bot5, bot6, bot7, bot8)
                .sortedBy { b -> b.pos.z }
        bot1 = bots[0]
        bot2 = bots[1]
        bot3 = bots[2]
        bot4 = bots[3]
        bot5 = bots[4]
        bot6 = bots[5]
        bot7 = bots[6]
        bot8 = bots[7]

        /* Step 1 - fill the bounding box */

        val (minCoord, maxCoord) = model.bbox
        yieldAll(moveTo(bots, minCoord))

        var xDirection = 1
        for (z in minCoord.z..maxCoord.z step bots.count()) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(normalMove(bots, 0, 1))
                for (x in minCoord.x..maxCoord.x) {
                    bots.forEach { b -> state.fill(b.id, Delta(0, -1, 0)) }
                    state.step()
                    yield(state)
                    if (x != maxCoord.x) {
                        yieldAll(normalMove(bots, xDirection, 0))
                    }
                }
                xDirection *= -1
            }

            if (z < maxCoord.z - bots.count()) {
                yieldAll(lateralMove(bots, bots.count(), Delta(xDirection, 1, 0)))
                yieldAll(normalMove(bots, 0, minCoord.y - maxCoord.y - 1))
            }
        }

        /* Step 2 - sculpt the box, leaving the correctly filled model */

        for (z in minCoord.z..maxCoord.z step bots.count()) {
            for (y in minCoord.y..maxCoord.y) {
                yieldAll(moveSculpt(bots, Delta(0, -1, 0)))
                for (x in minCoord.x..maxCoord.x) {
                    yieldAll(moveSculpt(bots, Delta(xDirection, 0, 0)))
                }
                xDirection *= -1
            }
            yieldAll(moveTo(bots,
                    bots[0].pos + Delta(0, maxCoord.y - minCoord.y + 1, -bots.count()),
                    Delta(xDirection, 1, 0)))
        }

        /* Step 3 - tear down */

        state.fusion(bot1.id, bot2.id)
        state.fusion(bot3.id, bot4.id)
        state.fusion(bot5.id, bot6.id)
        state.fusion(bot7.id, bot8.id)
        state.step()
        yield(state)

        state.lMove(bot1.id, Delta(xDirection, 0, 0), Delta(0, 0, 2))
        state.wait(bot3.id)
        state.sMove(bot5.id, Delta(0, 0, -1))
        state.lMove(bot7.id, Delta(xDirection, 0, 0), Delta(0, 0, -3))
        state.step()
        yield(state)

        state.fusion(bot1.id, bot3.id)
        state.fusion(bot7.id, bot5.id)
        state.step()
        yield(state)

        state.fusion(bot1.id, bot7.id)
        state.step()
        yield(state)

        yieldAll(moveTo(listOf(bot1), Coord.ZERO))

        state.halt(bot1.id)
        state.step()
        yield(state)
    }

    private fun moveTo(bots: List<BotView>, target: Coord, blowOutDirection: Delta = Delta(1, 1, 0)): Sequence<State> = buildSequence {
        val delta = target - bots[0].pos

        yieldAll(normalMove(bots, delta.dx, delta.dy))
        yieldAll(lateralMove(bots, delta.dz, blowOutDirection))
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

    private fun lateralMove(bots: List<BotView>, dz: Int, blowOutDirection: Delta): Sequence<State> = buildSequence {
        if (dz == 0) {
            return@buildSequence
        }

        val n = sqrt(bots.count().toDouble()).toInt()
        val blowOutDeltas = ArrayList<Pair<Delta, Delta>>()
        for (x in 0..n) {
            for (y in 0..n) {
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
