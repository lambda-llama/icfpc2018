package io.github.lambdallama

import java.util.*
import kotlin.coroutines.experimental.buildSequence

class LayeredStrategy(val model: Model) : Strategy {
    override val name: String = "Layered"
    override val state: State = State.forModel(model)

    private fun fillableFrom(coord: Coord): Coord? {
        val r = state.matrix.R - 1
        val arr = arrayOf(0, -1, 1)
        for (y in 1 downTo -1) {
            for (x in arr) {
                for (z in arr) {
                    if (x < 0 || y < 0 || z < 0 || x > r || y > r || z > r) {
                        continue
                    }
                    val delta = DeltaCoord(x, y, z)
                    if (!delta.isNear) {
                        continue
                    }
                    val option = coord + delta
                    if (!state.matrix[option]) {
                        return option
                    }
                }
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
                        .sortedWith(compareBy({ it.y }, { (it - bot.pos).mlen }))
                        .mapNotNull { toFill -> fillableFrom(toFill)?.let { toFill to it } }
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
                for (dxdydz in Matrix.DXDYDZ_MLEN1) {
                    val testCoord = coord + dxdydz
                    if (model.matrix[testCoord] && !state.matrix[testCoord]) {
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
