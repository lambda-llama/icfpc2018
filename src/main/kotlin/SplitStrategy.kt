package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence

fun sMove1Fill(state: State, id: Int, target: Coord) = buildSequence {
    val b = state[id]!!
    while (b.pos != target) {
        val delta = Matrix.DXDYDZ_MLEN1.minBy { ((b.pos + it) - target).clen }!!
        state.sMove(id, delta)
        state.step()
        yield(state)
        state.fill(id, -delta)
        state.step()
        yield(state)
    }

    check(b.pos == target)
}

class SplitStrategy(val mode: Mode, val model: Model, val source: Model?) : Strategy {
    override val name: String = "Split"
    override val state: State = State.create(mode, model.matrix, source?.matrix)

    override fun run(): Sequence<State> = buildSequence {
        val (minCoord, maxCoord) = model.matrix.bbox()
        val midX = minCoord.x + (maxCoord.x - minCoord.x) / 2

        val minMidCoord = minCoord.copy(x = midX)
        val maxMidCoord = maxCoord.copy(x = midX)

        val initialDelta = arrayOf(
            Delta(-1, 0, 0),
            Delta(0, -1, 0),
            Delta(0, 0, -1)
        ).first {
            val n = minMidCoord + it
            n.x >= 0 && n.y >= 0 && n.z >= 0
        }

        val id1 = 1
        val id2 = 2

        yieldAll(multiSLMove(state, id1, minMidCoord + initialDelta))
        yieldAll(sweep(state, model, id1, minMidCoord, maxMidCoord))

        state.fission(id1, Delta(0, 0, 1), state[id1]!!.seeds().count() - 1)
        state.step()
        yield(state)

        val its = mapOf(
            id1 to GroundedStrategy(mode, model, source, state)
                .fillAll(state[id1]!!, minCoord, maxMidCoord - Delta(1, 0, 0)).iterator(),
            id2 to GroundedStrategy(mode, model, source, state)
                .fillAll(state[id2]!!, minMidCoord + Delta(1, 0, 0), maxCoord).iterator())

        while (its.values.any { it.hasNext() }) {
            for ((id, it) in its) {
                if (it.hasNext()) {
                    it.next()
                } else {
                    state.wait(id)
                }
            }

            state.step()
            yield(state)
        }

        for (command in multiSLFind(state.narrow(id1), id1, state[id2]!!.pos - Delta(1, 0, 0))) {
            command(state, id1)
            state.wait(id2)
            state.step()
            yield(state)
        }
        state.fusion(id1, id2)
        state.step()
        yield(state)
    }
}