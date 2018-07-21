package io.github.lambdallama

import gnu.trove.impl.Constants
import gnu.trove.map.hash.TObjectIntHashMap
import gnu.trove.map.hash.TObjectLongHashMap
import java.util.*
import kotlin.coroutines.experimental.buildSequence

sealed class LList<T> {
    data class Cons<T>(val value: T, val next: LList<T>) : LList<T>() {
        override fun toString() = "$value->$next"

        override fun reversed(): LList<T> {
            var ptr: LList<T> = this
            var acc: LList<T> = Nil()
            while (ptr !is Nil<*>) {
                acc = Cons((ptr as Cons<T>).value, acc)
                ptr = ptr.next
            }

            return acc
        }
    }

    class Nil<T> : LList<T>() {
        override fun toString() = "Nil"

        override fun reversed() = this
    }

    abstract fun reversed(): LList<T>
}

private fun State.heuristic(id: Int, target: Coord): Int {
    // Energy leads us to the wrong direction!
    return (this[id]!!.pos - target).clen
}

/**
 * Moves the bot with ID `id` to `target` using minimal energy.
 *
 * Both S- and L-moves are used.
 */
fun multiSLMove(initial: State, id: Int, target: Coord): Sequence<State> {
    require(target.isInBounds(initial.matrix.R))

    val heuristic = TObjectIntHashMap<Coord>(
        Constants.DEFAULT_CAPACITY,
        Constants.DEFAULT_LOAD_FACTOR,
        Int.MAX_VALUE)
    val next = Matrix.zerosLike(initial.matrix)
    val q = PriorityQueue<Pair<State, LList<Command>>>(compareBy { it.first.heuristic(id, target) })
    q.add(initial to LList.Nil())
    var found: LList<Command>? = null
    while (q.isNotEmpty()) {
        val (state, commands) = q.poll()
        val b = state[id]!!
        if (b.pos == target) {
            found = commands
            break
        }

        next[b.pos] = false
        for ((command, n) in state.matrix.sNeighborhood(b.pos) + state.matrix.lNeighborhood(b.pos)) {
            if (!next[b.pos]) {
                val split = state.shallowSplit()
                command(split, b.id)
                split.step()
                val h = split.heuristic(b.id, target)
                if (h < heuristic[n]) {
                    q.add(split to LList.Cons(command, commands))
                    heuristic.put(n, h)
                    next[n] = true
                }
            }
        }
    }

    if (found == null) {
        error("failed to multiSLMove: ${initial[id]!!.pos} -> $target")
    }

    // Mutate initial in-place!
    return buildSequence {
        var ptr = found.reversed()
        while (ptr !is LList.Nil) {
            (ptr as LList.Cons<Command>).value(initial, id)
            initial.step()
            yield(initial)
            ptr = ptr.next
        }
    }
}

class Baseline(private val model: Model) : Strategy {
    override val name: String = "Baseline"
    override val state: State = State.forModel(model)

    override fun run(): Sequence<State> = buildSequence {
        yield(state)
        val (minCoord, maxCoord) = model.bbox
        val initialDelta = arrayOf(
            DeltaCoord(-1, 0, 0),
            DeltaCoord(0, -1, 0),
            DeltaCoord(0, 0, -1)
        ).first {
            val n = minCoord + it
            n.x >= 0 && n.y >= 0 && n.z >= 0
        }

        val id = 1
        state.flip(id)
        state.step()
        yield(state)
        yieldAll(multiSLMove(state, id, minCoord + initialDelta))

        val b = state[id]!!
        var x = minCoord.x
        var dx = 1
        var z = minCoord.z
        var dz = 1
        for (y in minCoord.y..maxCoord.y) {
            val targetX = if (dx > 0) maxCoord.x else minCoord.x
            while (x != targetX + dx) {
                val targetZ = if (dz > 0) maxCoord.z else minCoord.z
                while (z != targetZ + dz) {
                    val coord = Coord(x, y, z)
                    val bPos = b.pos
                    val delta = coord - bPos
                    state.sMove(id, delta)
                    state.step()
                    yield(state)
                    if (model.matrix[bPos]) {
                        state.fill(id, -delta)
                        state.step()
                        yield(state)
                    }
                    z += dz
                }
                x += dx

                z -= dz  // go back.
                dz = -dz
            }

            x -= dx  // go back.
            dx = -dx
        }
        check(state.matrix == model.matrix)

        state.flip(id)
        state.step()
        yield(state)
        yieldAll(multiSLMove(state, id, Coord.ZERO))
        state.halt(id)
        state.step()
        yield(state)
    }
}
