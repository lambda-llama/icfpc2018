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

        for ((gtd, n) in state.matrix.voidS2FillNeighborhood(b.pos)) {
            if (!next[b.pos]) {
                val split = state.shallowSplit()
                for (command in gtd) {
                    command(split, id)
                    split.step()
                }
                val h = split.heuristic(b.id, target)
                if (h < heuristic[n]) {
                    q.add(split to gtd.fold(commands) { ptr, command -> ptr.cons(command) })
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

fun sweep(
    state: State,
    model: Model,
    id: Int,
    minCoord: Coord,
    maxCoord: Coord
) = buildSequence {
    state.flip(id)
    state.step()
    yield(state)

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

    state.flip(id)
    state.step()
    yield(state)
}

class Baseline(
    private val mode: Mode,
    private val model: Model,
    override val state: State = State.forModel(model)
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

        sweep(state, model, id, minCoord, maxCoord)
        //check(state.matrix == model.matrix)

        yieldAll(multiSLMove(state, id, Coord.ZERO))
        state.halt(id)
        state.step()
        yield(state)
    }
}
