package io.github.lambdallama

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

/**
 * Moves the bot with ID `id` to `target` using minimal energy.
 *
 * Both S- and L-moves are used.
 */
fun multiSLMove(initial: State, id: Int, target: Coord): Sequence<State> {
    require(target.isInBounds(initial.matrix.R))

    val seen = HashSet<Coord>()
    val next = HashSet<Coord>()
    val q = PriorityQueue<Pair<State, LList<Command>>>(compareBy { it.first.energy })
    q.add(initial.shallowSplit() to LList.Nil())
    var found: LList<Command>? = null
    while (q.isNotEmpty()) {
        val (state, commands) = q.poll()
        val b = state[id]!!
        if (b.pos == target) {
            found = commands
            break
        }

        seen.add(b.pos)

        for ((command, n) in state.matrix.sNeighborhood(b.pos) + state.matrix.lNeighborhood(b.pos)) {
            if (n !in seen && n !in next) {
                val split = state.shallowSplit()
                command(split, b.id)
                split.step()
                q.add(split to LList.Cons(command, commands))
                next.add(n)
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
