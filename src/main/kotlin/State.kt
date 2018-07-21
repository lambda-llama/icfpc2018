package io.github.lambdallama

import java.util.*

class State(
    val matrix: Matrix,
    val bots: Array<Bot?>,
//    private val volatile: Matrix = Matrix.zerosLike(matrix),
    harmonics: Harmonics = Harmonics.Low,
    energy: Long = 0,
    private var expectedBotActionsThisStep: Int = bots.count { it != null }
) {
    private val traceListeners: ArrayList<TraceListener> = ArrayList()

    private val botCommands: HashMap<Int, Command> = HashMap()
    var harmonics: Harmonics = harmonics
        private set
    var energy: Long = energy
        private set

    /* Private methods */

    /* General public methods */

    fun split() = State(
        matrix.copy(coordinates = matrix.coordinates.clone()),
        Array(bots.size) { bots[it]?.copy() },
        harmonics,
        energy,
        expectedBotActionsThisStep)

    fun shallowSplit() = State(
        matrix,
        Array(bots.size) { bots[it]?.copy() },
        harmonics,
        energy,
        expectedBotActionsThisStep)

    fun addTraceListener(traceListener: TraceListener) {
        traceListeners.add(traceListener)
    }

    fun botIds(): Sequence<Int> = bots.asSequence()
        .withIndex()
        .filterNot { it.value == null }
        .map { it.index }

    operator fun get(id: Int): BotView? = bots[id]

    /* Commands */

    fun halt(id: Int) {
        check(bots.count { it != null } == 1)
        val bot = checkNotNull(bots[id])
        check(bot.pos == Coord.ZERO)
        check(harmonics == Harmonics.Low)

        bots[id] = null
        botCommands[id] = Halt
//        volatile[bot.pos] = true
    }

    fun wait(id: Int) {
        val bot = checkNotNull(bots[id])
        botCommands[id] = Wait
//        volatile[bot.pos] = true
    }

    fun flip(id: Int) {
        val bot = checkNotNull(bots[id])
        harmonics = harmonics.flip()
        botCommands[id] = Flip
//        volatile[bot.pos] = true
    }

    fun sMove(id: Int, delta: Delta) {
        require(delta.isLongLinear) { "not long-linear: $delta" }
        val bot = checkNotNull(bots[id])
        val oldPos = bot.pos
        val newPos = oldPos + delta

//        volatile[oldPos, newPos] = true
        assert(matrix.isVoidRegion(oldPos, newPos))
        assert(newPos.isInBounds(matrix.R)) { "out of bounds: $newPos R = ${matrix.R}" }

        bot.pos = newPos
        energy += 2 * delta.mlen
        botCommands[id] = SMove(delta)
    }

    fun lMove(id: Int, delta0: Delta, delta1: Delta) {
        require(delta0.isShortLinear)
        require(delta1.isShortLinear)
        val bot = checkNotNull(bots[id])
        val oldPos = bot.pos
        val midPos = oldPos + delta0
        val newPos = midPos + delta1
        assert(midPos.isInBounds(matrix.R)) { "out of bounds: $midPos R = ${matrix.R}" }
        assert(newPos.isInBounds(matrix.R)) { "out of bounds: $newPos R = ${matrix.R}" }

        assert(matrix.isVoidRegion(oldPos, midPos))
        assert(matrix.isVoidRegion(midPos, newPos))
//        volatile[oldPos, midPos] = true
//        volatile[midPos, newPos] = true

        bot.pos = newPos
        energy += 2 * (delta0.mlen + 2 + delta1.mlen)
        botCommands[id] = LMove(delta0, delta1)
    }

    fun fill(id: Int, delta: Delta) {
        require(delta.isNear)
        val bot = checkNotNull(bots[id])
        val fillPos = bot.pos + delta
        assert(fillPos.isInBounds(matrix.R))

        if (matrix[fillPos]) {
            energy += 6
        } else {
            matrix[fillPos] = true
            energy += 12
        }
        botCommands[id] = Fill(delta)
//        volatile[bot.pos] = true
//        volatile[fillPos] = true
    }

    fun void(id: Int, delta: Delta) {
        check(delta.isNear)
        val bot = checkNotNull(bots[id])
        val voidPos = bot.pos + delta
        assert(voidPos.isInBounds(matrix.R))

        if (matrix[voidPos]) {
            matrix[voidPos] = false
            energy -= 12
        } else {
            energy += 3
        }
        botCommands[id] = Void(delta)
    }

    fun fission(id: Int, delta: Delta, m: Int) {
        check(delta.isNear)
        val bot = checkNotNull(bots[id])
        check(bot.seeds.any())
        val newBotPos = bot.pos + delta
        check(newBotPos.isInBounds(matrix.R))
        check(!matrix[newBotPos])
        check(m < bot.seeds.count())

        val split = bot.seeds.elementAt(m) + 1
        val newBotSeeds = bot.seeds.headSet(split)
        bot.seeds = bot.seeds.tailSet(split)
        val newBotId = newBotSeeds.first()
        newBotSeeds.remove(newBotId)
        val newBot = Bot(newBotId, newBotPos, newBotSeeds)
        bots[newBotId] = newBot

        energy += 24
        botCommands[id] = Fission(delta, m)
//        volatile[bot.pos] = true
//        volatile[newBotPos] = true
    }

    fun fusion(pId: Int, sId: Int) {
        val pBot = checkNotNull(bots[pId])
        val sBot = checkNotNull(bots[sId])
        check((pBot.pos - sBot.pos).isNear)

        bots[sBot.id] = null
        pBot.seeds.add(sBot.id)
        pBot.seeds.addAll(sBot.seeds)
        energy -= 24
        botCommands[pId] = FusionP(sBot.pos - pBot.pos)
        botCommands[sId] = FusionS(pBot.pos - sBot.pos)
//        volatile[sBot.pos] = true
//        volatile[pBot.pos] = true
    }

    fun gFillLine(id0: Int, i1: Int, d0: Delta, d1: Delta) {
        TODO()
    }

    fun gFillPlane(id0: Int, i1: Int, i2: Int, i3: Int,
                   d0: Delta, d1: Delta, d2: Delta, d3: Delta) {
        TODO()
    }

    fun gFillBox(id0: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int,
                 d0: Delta, d1: Delta, d2: Delta, d3: Delta, d4: Delta, d5: Delta, d6: Delta, d7: Delta) {
        TODO()
    }

    fun gVoidLine(id0: Int, i1: Int, d0: Delta, d1: Delta) {
        TODO()
    }

    fun gVoidPlane(id0: Int, i1: Int, i2: Int, i3: Int,
                   d0: Delta, d1: Delta, d2: Delta, d3: Delta) {
        TODO()
    }

    fun gVoidBox(id0: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int,
                 d0: Delta, d1: Delta, d2: Delta, d3: Delta, d4: Delta, d5: Delta, d6: Delta, d7: Delta) {
        TODO()
    }

    fun step() {
        check(botCommands.count() == expectedBotActionsThisStep)

//        volatile.clear()

        val volume = matrix.R * matrix.R * matrix.R
        energy += (if (harmonics == Harmonics.High) 30 else 3) * volume
        energy += 20 * expectedBotActionsThisStep

        expectedBotActionsThisStep = bots.count { it != null }

        if (traceListeners.isNotEmpty()) {
            val commands = botCommands.toSortedMap()
            for (listener in traceListeners) {
                listener.onStep(commands)
            }
        }

        botCommands.clear()
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is State -> false
            else ->
                energy == other.energy &&
                    harmonics == other.harmonics &&
                    expectedBotActionsThisStep == other.expectedBotActionsThisStep &&
                    matrix == other.matrix &&
                    Arrays.equals(bots, other.bots)
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(energy, harmonics, expectedBotActionsThisStep, matrix, bots)
    }

    companion object {
        fun forModel(model: Model): State {
            // TODO: use the model matrix?
            val bots = Array<Bot?>(20 + 1) { null }
            bots[1] = Bot(1, Coord.ZERO, (2..20).toSortedSet())
            return State(Matrix.zerosLike(model.matrix), bots)
        }
    }
}

enum class Harmonics {
    Low,
    High;

    fun flip(): Harmonics = when (this) {
        Low -> High
        High -> Low
    }
}
