package io.github.lambdallama

import kotlin.coroutines.experimental.buildSequence

private val DX1 = Delta(1, 0, 0)
private val DZ1 = Delta(0, 0, 1)

class SplitStrategy(val mode: Mode, val model: Model, val source: Model?) : Strategy {
    override val name: String = "Split"
    override val state: State = State.create(mode, model.matrix, source?.matrix)

    override fun run(): Sequence<State> = buildSequence {
        yield(state)

        val (minCoord, maxCoord) = model.matrix.bbox() // wrong>
        val id1 = 1
        for (ignore in fillAll(id1, from = minCoord, to = maxCoord)) {
            state.step()
            yield(state)
        }

        for (command in multiSLFind(state, id1, Coord.ZERO)) {
            command(state, id1)
            state.step()
            yield(state)
        }
    }

    private fun await(sequences: Map<Int, Sequence<State>>): Sequence<State> = buildSequence {
        val its = sequences.mapValues { it.value.iterator() }
        while (its.values.any { it.hasNext() }) {
            for ((id, it) in its) {
                if (it.hasNext()) {
                    it.next()
                } else {
                    state.wait(id)
                }
            }

            yield(state)
        }
    }

    private fun grounded(id: Int, mask: Int, from: Coord, to: Coord): Sequence<State> {
        // mask < 0 -- left | mask = 0 -- both | mask > 0 -- right
        val grounded = HashSet<Coord>()
        if (mask >= 0) {
            state.matrix.forEach(from, to.copy(x = from.x)) { x, y, z ->
                if (state.targetMatrix[x, y, z]) {
                    grounded.add(Coord(x, y, z))
                }
            }
        }

        if (mask <= 0) {
            state.matrix.forEach(from.copy(x = to.x), to) { x, y, z ->
                if (state.targetMatrix[x, y, z]) {
                    grounded.add(Coord(x, y, z))
                }
            }
        }

        return GroundedStrategy(mode, model, source, state).fillAll(id, grounded, from, to)
    }

    private fun fillWall(id: Int, wallMin: Coord, wallMax: Coord) = buildSequence {
        for (command in multiSLFind(state.narrow(id), id, wallMin - DZ1)) {
            command(state, id)
            yield(state)
        }

        yieldAll(sweepFill(state, { true }, id, wallMin, wallMax))

        // Fill the remaining gap in the wall.
        state.sMove(id, DX1)
        yield(state)
        state.fill(id, -DX1)
        yield(state)
    }

    private fun eraseWall(id: Int, wallMin: Coord, wallMax: Coord) = buildSequence {
        // Erase the wall accounting for the glitch in the bottom left corner.
        for (command in multiSLFind(state.narrow(id), id, wallMin - DZ1 * 2)) {
            command(state, id)
            yield(state)
        }

        state.void(id, DZ1)
        yield(state)
        state.sMove(id, DZ1)

        yieldAll(sweepVoid(
            state,
            { !state.targetMatrix[it] },
            id,
            wallMin,
            wallMax))
    }

    private fun fuseTwo(id1: Int, id2: Int): Sequence<State> = buildSequence {
        val target = state[id2]!!.pos
        val delta = Matrix.DXDYDZ_MLEN1.first { !state.matrix[target + it] }
        for (command in multiSLFind(state.narrow(id1), id1, target + delta)) {
            command(state, id1)
            state.wait(id2)
            yield(state)
        }

        state.fusion(id1, id2)
        yield(state)
    }

    private fun fillAll(id1: Int, from: Coord, to: Coord): Sequence<State> = buildSequence {
        val nBots = Math.max(3, Math.min(40, (to.x - from.x) / 10))
        val bots = arrayListOf(state[id1]!!)
        for (i in 2..nBots - 1) {
            bots.dropLast(1).forEach { b -> state.wait(b.id) }
            val id = state.fission(bots.last().id, DX1, bots.last().seeds().count() - 2)
            bots.add(state[id]!!)
            yield(state)
        }

        val chunks = Array(nBots - 1) { idx ->
            val wallX = from.x + (to.x - from.x) * (idx + 1) / nBots
            from.copy(x = wallX) to to.copy(x = wallX)
        }

        val fillWalls = HashMap<Int, Sequence<State>>()
        fillWalls[bots.last().id] = emptySequence()
        for ((bot, chunk) in bots.zip(chunks)) {
            fillWalls[bot.id] = fillWall(bot.id, chunk.first, chunk.second)
        }

        yieldAll(await(fillWalls))

        // Create one more bot for the last region.
        bots.dropLast(1).forEach { b -> state.wait(b.id) }
        val id = state.fission(bots.last().id, DX1, bots.last().seeds().count() - 2)
        bots.add(state[id]!!)
        yield(state)

        val fillRegions = HashMap<Int, Sequence<State>>()
        fillRegions[bots.last().id] = grounded(
            bots.last().id,
            mask = 1,
            from = chunks.last().first + DX1,
            to = to)
        for ((idx, p) in bots.zip(chunks).withIndex()) {
            val (bot, chunk) = p
            fillRegions[bot.id] = grounded(
                bot.id,
                mask = if (idx > 0) 0 else -1,
                from = if (idx == 0) from else chunks[idx - 1].first + DX1,
                to = chunk.second - DX1)
        }

        yieldAll(await(fillRegions))

        // There're -1 walls, so the last bot can be removed.
        val fuseLast = HashMap<Int, Sequence<State>>()
        fuseLast[1] = fuseTwo(1, bots.last().id)
        for (bot in bots.drop(1).dropLast(1)) {
            fuseLast[bot.id] = emptySequence()
        }
        yieldAll(await(fuseLast))
        bots.removeAt(bots.size - 1)

        val eraseWalls = HashMap<Int, Sequence<State>>()
        eraseWalls[bots.last().id] = emptySequence()
        for ((bot, chunk) in bots.zip(chunks)) {
            eraseWalls[bot.id] = eraseWall(bot.id, chunk.first, chunk.second)
        }

        yieldAll(await(eraseWalls))

        while (bots.size > 1) {
            val fuseAll = HashMap<Int, Sequence<State>>()
            val removed = ArrayList<BotView>()
            for (idx in 0 until bots.size step 2) {
                if (idx == bots.size - 1) {
                    fuseAll[bots.last().id] = emptySequence()
                } else {
                    val b1 = bots[idx]
                    val b2 = bots[idx + 1]
                    fuseAll[b1.id] = fuseTwo(b1.id, b2.id)
                    removed.add(b2)
                }
            }

            yieldAll(await(fuseAll))
            bots.removeAll(removed)
        }

        check(bots.single().id == 1)
    }
}