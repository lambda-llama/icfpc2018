package io.github.lambdallama

import java.util.*
import kotlin.coroutines.experimental.buildSequence

class LayeredStrategy(val model: Model) : Strategy {
    override val name: String = "Layered"
    override val state: State = State.forModel(model)

    private fun fillableFrom(coord: Coord): Coord? {
        for (delta in Matrix.NEAR_COORD_DIFFERENCE) {
            check(delta.isNear)
            val option = coord + delta
            if (option.isInBounds(state.matrix.R) && !state.matrix[option]) {
                return option
            }
        }
        return null
    }

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        val layer = HashSet<Coord>()
        for (x in 0 until model.matrix.R) {
            for (z in 0 until model.matrix.R) {
                val coord = Coord(x, 0, z)
                if (model.matrix[coord]) {
                    layer.add(coord)
                }
            }
        }

        val bot = state[1]!!

        do {
            val prevLayer = layer.toSet()
            var filled = false
            while (layer.any()) {

                val (toFill, fillFrom) = layer
                        .asSequence()
                        .mapNotNull { toFill -> fillableFrom(toFill)?.let { toFill to it } }
                        .sortedWith(compareBy({ it.first.y }, { (it.second - bot.pos).mlen }))
                        .firstOrNull() ?: break
                filled = true
                layer.remove(toFill)
                yieldAll(multiSLMove(state, bot.id, fillFrom))
                state.fill(bot.id, toFill - bot.pos)
                state.step()
                yield(state)
            }
            if (!filled) {
                error("Failed to fill anything")
            }

            for (coord in prevLayer) {
                for (dxdydz in Matrix.NEAR_COORD_DIFFERENCE) {
                    val testCoord = coord + dxdydz
                    if (testCoord.isInBounds(model.matrix.R) &&
                            model.matrix[testCoord] &&
                            !state.matrix[testCoord]) {
                        layer.add(testCoord)
                    }
                }
            }
        } while (layer.any())

        check(state.matrix == model.matrix)

        yieldAll(multiSLMove(state, bot.id, Coord.ZERO))
        state.halt(bot.id)
        state.step()
        yield(state)
    }
}
