package io.github.lambdallama

import gnu.trove.impl.Constants
import gnu.trove.map.hash.TObjectIntHashMap
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

    fun cons(value: T): LList<T> = LList.Cons(value, this)

    fun asSequence(): Sequence<T> = buildSequence {
        var ptr = this@LList
        while (ptr !is Nil<*>) {
            yield((ptr as Cons<T>).value)
            ptr = ptr.next
        }
    }
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
fun multiSLFind(initial: State, id: Int, target: Coord): Sequence<Command> {
    require(target.isInBounds(initial.matrix))

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
                    q.add(split to commands.cons(command))
                    heuristic.put(n, h)
                    next[n] = true
                }
            }
        }
    }

    if (found == null) {
        error("failed to multiSLMove: ${initial[id]!!.pos} -> $target")
    }

    return found.reversed().asSequence()
}


fun multiSLMove(initial: State, id: Int, target: Coord): Sequence<State> {
    return multiSLFind(initial, id, target).map { command ->
        // Mutate initial in-place!
        command(initial, id)
        initial.step()
        initial
    }
}

inline fun sweepFill(
    state: State,
    crossinline shouldFill: (Coord) -> Boolean,
    id: Int,
    minCoord: Coord,
    maxCoord: Coord
) = buildSequence {
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
                yield(state)
                if (shouldFill(bPos)) {
                    state.fill(id, -delta)
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
}

inline fun sweepVoid(
    state: State,
    crossinline shouldVoid: (Coord) -> Boolean,
    id: Int,
    minCoord: Coord,
    maxCoord: Coord
) = buildSequence {
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
                if (state.harmonics == Harmonics.Low) {
                    state.flip(id)
                    yield(state)
                }

                val coord = Coord(x, y, z)
                val bPos = b.pos
                val delta = coord - bPos
                state.void(id, delta)
                yield(state)
                state.sMove(id, delta)
                yield(state)
                if (!shouldVoid(bPos)) {
                    state.fill(id, -delta)
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

    if (state.harmonics == Harmonics.High) {
        state.flip(id)
        yield(state)
    }
}

class Baseline(
    private val mode: Mode,
    private val model: Model,
    source: Model?,
    override val state: State = State.create(mode, model.matrix, source?.matrix)
) : Strategy {
    override val name: String = "Baseline"

    override fun run(): Sequence<State> = buildSequence {
        yield(state)
        val (minCoord, maxCoord) = model.matrix.bbox()
        check(minCoord.isInBounds(state.matrix))
        check(maxCoord.isInBounds(state.matrix))
        val initialDelta = Matrix.DXDYDZ_MLEN1.first { (minCoord + it).isInBounds(state.matrix) }

        val id = 1
        yieldAll(multiSLMove(state, id, minCoord + initialDelta))

        state.flip(id)
        state.step()
        yield(state)

        for (ignore in sweepFill(state, model.matrix::get, id, minCoord, maxCoord)) {
            state.step()
            yield(state)
        }

        state.flip(id)
        state.step()
        yield(state)

        check(state.matrix == state.targetMatrix)

        yieldAll(multiSLMove(state, id, Coord.ZERO))
        state.halt(id)
        state.step()
        yield(state)
    }
}
