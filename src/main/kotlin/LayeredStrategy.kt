package io.github.lambdallama

import java.util.*
import kotlin.collections.HashMap
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LayeredStrategy(val model: Model) : Strategy {
    override val name: String = "Layered"
    override val state: State = State.forModel(model)

    private fun reachableFrom(start: Coord): Sequence<Coord> {
        return (model.matrix.sNeighborhood(start) + model.matrix.lNeighborhood(start))
            .map { (_, n) -> n }
    }

    private fun estimateCost(start: Coord, end: Coord): Int {
        return ((Math.abs(start.x - end.x) + 14) / 15) +
            ((Math.abs(start.y - end.y) + 14) / 15) +
            ((Math.abs(start.z - end.z) + 14) / 15)
    }

    private fun goto(bot: BotView, target: Coord): List<Coord> {
        val closedSet = HashSet<Coord>()
        val openSet = HashSet<Coord>()
        openSet.add(bot.pos)
        val cameFrom = HashMap<Coord, Coord>()
        val gScore = HashMap<Coord, Int>().withDefault { Int.MAX_VALUE }
        gScore[bot.pos] = 0
        val fScore = HashMap<Coord, Int>().withDefault { Int.MAX_VALUE }
        fScore[bot.pos] = estimateCost(bot.pos, target)

        while (openSet.any()) {
            val current = openSet.minBy { c -> fScore[c]!! }!!
            if (current == target) {
                val path = ArrayList<Coord>()
                path.add(current)
                var prev = current
                while (cameFrom.containsKey(prev)) {
                    prev = cameFrom[prev]!!
                    path.add(prev)
                }
                return path.reversed().drop(1)
            }

            openSet.remove(current)
            closedSet.add(current)

            for (coord in reachableFrom(current)) {
                if (closedSet.contains(coord)) {
                    continue
                }

                val tentativeGScore = gScore[current]!! + 1

                if (!openSet.contains(coord)) {
                    openSet.add(coord)
                } else if (tentativeGScore >= gScore[current]!!) {
                    continue
                }

                cameFrom[coord] = current
                gScore[coord] = tentativeGScore
                fScore[coord] = gScore[coord]!! + estimateCost(coord, target)
            }
        }

        throw Exception("Failed to find a path")
    }

    private fun fillableFrom(coord: Coord): Coord {
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
        throw Exception("Failed to find a coord to fill the target from")
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

        val bot = state.getBot(1)!!

        do {
            val prevLayer = layer.toSet()
            while (layer.any()) {
                val next = layer.minBy { c -> (c - bot.pos).mlen }!!
                layer.remove(next)
                for (step in goto(bot, fillableFrom(next))) {
                    move(bot, step)
                    state.step()
                    yield(state)
                }
                state.fill(bot.id, next - bot.pos)
                state.step()
                yield(state)
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

        for (step in goto(bot, Coord.ZERO)) {
            move(bot, step)
            state.step()
            yield(state)
        }
        state.halt(bot.id)
        state.step()
        yield(state)
    }

    private fun move(bot: BotView, reachableTarget: Coord) {
        val delta = reachableTarget - bot.pos
        if (delta.isLongLinear) {
            state.sMove(bot.id, delta)
        } else {
            val delta0: DeltaCoord
            val delta1: DeltaCoord
            when {
                delta.dx == 0 -> {
                    delta0 = DeltaCoord(0, delta.dy, 0)
                    delta1 = DeltaCoord(0, 0, delta.dz)
                }
                delta.dy == 0 -> {
                    delta0 = DeltaCoord(delta.dx, 0, 0)
                    delta1 = DeltaCoord(0, 0, delta.dz)
                }
                else -> {
                    delta0 = DeltaCoord(delta.dx, 0, 0)
                    delta1 = DeltaCoord(0, delta.dy, 0)
                }
            }
            val okDelta0First = testDeltaReachability(bot.pos, delta0)
            val okDelta1First = testDeltaReachability(bot.pos, delta1)
            val okDelta0Second = testDeltaReachability(bot.pos + delta1, delta0)
            val okDelta1Second = testDeltaReachability(bot.pos + delta0, delta1)
            when {
                okDelta0First && okDelta1Second -> state.lMove(bot.id, delta0, delta1)
                okDelta1First && okDelta0Second -> state.lMove(bot.id, delta1, delta0)
                else -> throw Exception("Invalid move")
            }
        }
    }

    private fun testDeltaReachability(base: Coord, delta: DeltaCoord): Boolean {
        val tests = if (delta.dx != 0) {
            val sign = if (delta.dx > 0) 1 else -1
            (1..abs(delta.dx)).map { x -> DeltaCoord(sign * x, 0, 0) }
        } else if (delta.dy != 0) {
            val sign = if (delta.dy > 0) 1 else -1
            (1..abs(delta.dy)).map { y -> DeltaCoord(0, sign * y, 0) }
        } else {
            val sign = if (delta.dz > 0) 1 else -1
            (1..abs(delta.dz)).map { z -> DeltaCoord(0, 0, sign * z) }
        }
        return tests.all { t -> !state.matrix[base + t] }
    }
}
