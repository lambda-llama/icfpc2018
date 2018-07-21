package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence
import kotlin.math.min

fun State.multiSMove(b: BotView, target: Coord): Sequence<State> {
    val state = this
    return buildSequence {
        while (b.pos.x < target.x) {
            sMove(b.id, DeltaCoord(min(5, target.x - b.pos.x), 0, 0))
            step()
            yield(state)
        }
        while (b.pos.y < target.y) {
            sMove(b.id, DeltaCoord(0, min(5, target.y - b.pos.y), 0))
            step()
            yield(state)
        }
        while (b.pos.z < target.z) {
            sMove(b.id, DeltaCoord(0, 0, min(5, target.z - b.pos.z)))
            step()
            yield(state)
        }
    }
}

class Baseline(val model: Model) : Strategy {
    override val name: String = "Baseline"
    override val state: State = State.forModel(model)

    override fun run(): Sequence<State> = buildSequence {
        yield(state)
        val (minCoord, maxCoord) = model.bbox
        val b = state.getBot(1)

        state.flip(b.id)
        state.step()
        yield(state)
        state.multiSMove(b, minCoord + DeltaCoord(-1, -1, -1))

        for (y in 0 until model.matrix.R) {
            model.matrix.forEach(minCoord.copy(y = y), maxCoord.copy(y = y)) { x, _, z ->
                val coord = Coord(x, y, z)
                if (b.pos == coord) {
                    return@forEach
                }

                val delta = coord - b.pos  // mlen == 1.
                if (model.matrix[coord]) {
                    state.fill(b.id, delta)
                    state.step()
                    yield(state)
                }
                state.sMove(b.id, delta)
                state.step()
                yield(state)
            }
        }
        check(state.matrix == model.matrix)

        state.flip(b.id)
        state.step()
        yield(state)
        state.multiSMove(b, Coord.ZERO)
        state.halt(b.id)
        state.step()
        yield(state)
    }
}
