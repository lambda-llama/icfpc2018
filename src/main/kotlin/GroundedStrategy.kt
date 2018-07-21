package io.github.lambdallama

import io.github.lambdallama.Matrix.Companion.DXDYDZ_MLEN1
import io.github.lambdallama.Matrix.Companion.NEAR_COORD_DIFFERENCE
import java.util.HashSet
import kotlin.coroutines.experimental.buildSequence

class GroundedStrategy(
    val model: Model,
    override val state: State = State.forModel(model)
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

        val grounded = HashSet<Coord>()
        model.matrix.apply {
            for (x in from.x..to.x) {
                for (z in from.z..to.z) {
                    val coord = Coord(x, 0, z)
                    if (this[coord]) grounded.add(coord)
                }
            }
        }

        val bot = state[1]!!

        while (grounded.isNotEmpty()) {
            val (toFill, fillFrom) = grounded.mapNotNull { toFill -> fillableFrom(toFill)?.let { toFill to it } }
                    .sortedWith(compareBy({ it.first.y }, { -it.second.y }, { (it.second - bot.pos).mlen }))
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
                if (testCoord.isInBounds(model.matrix) &&
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
