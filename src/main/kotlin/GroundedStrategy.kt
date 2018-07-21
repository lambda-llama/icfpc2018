package io.github.lambdallama

import io.github.lambdallama.Matrix.Companion.DXDYDZ_MLEN1
import io.github.lambdallama.Matrix.Companion.NEAR_COORD_DIFFERENCE
import java.util.HashSet
import kotlin.coroutines.experimental.buildSequence


class GroundedStrategy(val model: Model) : Strategy {
    override val name: String = "Grounded"
    override val state: State = State.forModel(model)

    private fun fillableFrom(coord: Coord): Coord? {
        for (d in NEAR_COORD_DIFFERENCE) {
            val option = coord + d
            if (option.isInBounds(state.matrix.R) && !state.matrix[option]) {
                return option
            }
        }
        return null
    }

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        val grounded = HashSet<Coord>()
        for (x in 0 until model.matrix.R) {
            for (z in 0 until model.matrix.R) {
                val coord = Coord(x, 0, z)
                if (model.matrix[coord]) grounded.add(coord)
            }
        }

        val bot = state[1]!!

        while (grounded.isNotEmpty()) {
            val (toFill, fillFrom) = grounded.mapNotNull { toFill -> fillableFrom(toFill)?.let { toFill to it } }
                    .sortedWith(compareBy({ it.first.y }, { (it.second - bot.pos).mlen }))
                    .firstOrNull() ?: error("unfillable grounded cell")

            grounded.remove(toFill)
            yieldAll(multiSLMove(state, bot.id, fillFrom))
            state.fill(bot.id, toFill - bot.pos)
            state.step()
            yield(state)

            for (dxdydz in DXDYDZ_MLEN1) {
                check(dxdydz.mlen == 1)
                check(model.matrix[toFill])
                val testCoord = toFill + dxdydz
                if (testCoord.isInBounds(model.matrix.R) &&
                        model.matrix[testCoord] &&
                        !state.matrix[testCoord]) {
                    grounded.add(testCoord)
                }
            }
        }

        check(state.matrix == model.matrix)

        yieldAll(multiSLMove(state, bot.id, Coord.ZERO))
        state.halt(bot.id)
        state.step()
        yield(state)
    }
}
