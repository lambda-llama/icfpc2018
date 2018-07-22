package io.github.lambdallama

import io.github.lambdallama.Matrix.Companion.DXDYDZ_MLEN1
import io.github.lambdallama.Matrix.Companion.NEAR_COORD_DIFFERENCE
import java.util.HashSet
import kotlin.coroutines.experimental.buildSequence

class GroundedStrategy(
    val mode: Mode,
    val model: Model,
    source: Model?,
    override val state: State = State.create(mode, model.matrix, source?.matrix)
) : Strategy {
    override val name: String = "Grounded"

    private fun fillableFrom(coord: Coord): Coord? {
        return NEAR_COORD_DIFFERENCE
                .map { coord + it }
                .filter { it.isInBounds(state.matrix) && !state.matrix[it] }
                .maxBy { it.y }
    }

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        val bot = state[1]!!
        for (ignore in fillAll(bot)) {
            state.step()
            yield(state)
        }

        check(state.matrix == model.matrix)

        yieldAll(multiSLMove(state, bot.id, Coord.ZERO))
        state.halt(bot.id)
        state.step()
        yield(state)
    }

    fun fillAll(
        bot: BotView,
        from: Coord = model.matrix.from,
        to: Coord = model.matrix.to
    ): Sequence<State> = buildSequence {
        val grounded = HashSet<Coord>()
        for (x in from.x..to.x) {
            for (z in from.z..to.z) {
                val coord = Coord(x, 0, z)
                if (model.matrix[coord]) grounded.add(coord)
            }
        }

        while (grounded.isNotEmpty()) {
            val (toFill, fillFrom) = grounded.mapNotNull { toFill -> fillableFrom(toFill)?.let { toFill to it } }
                .sortedWith(compareBy({ it.first.y }, { -it.second.y }, { (it.second - bot.pos).mlen }))
                .firstOrNull() ?: error("unfillable grounded cell")

            grounded.remove(toFill)

            for (command in multiSLFind(state.narrow(bot.id), bot.id, fillFrom)) {
                command(state, bot.id)
                yield(state)
            }

            state.fill(bot.id, toFill - bot.pos)
            yield(state)

            for (dxdydz in DXDYDZ_MLEN1) {
                check(dxdydz.mlen == 1)
                check(model.matrix[toFill])
                val testCoord = toFill + dxdydz
                if (testCoord.isInBounds(model.matrix) &&
                    testCoord.isInBox(from to to) &&
                    model.matrix[testCoord] &&
                    !state.matrix[testCoord]) {
                    grounded.add(testCoord)
                }
            }
        }
    }
}
