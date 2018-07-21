package io.github.lambdallama

import com.google.common.collect.Sets
import java.util.*

class State(
    val matrix: Matrix,
    val bots: MutableMap<Int, Bot>,
    private val volatile: Matrix = Matrix.zerosLike(matrix),
    harmonics: Harmonics = Harmonics.Low,
    energy: Long = 0,
    private var expectedBotActionsThisStep: Int = bots.count()
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
        bots.mapValues { it.value.copy() } as MutableMap<Int, Bot>,
        volatile.copy(coordinates = volatile.coordinates.clone()),
        harmonics,
        energy,
        expectedBotActionsThisStep)

    fun shallowSplit() = State(
        matrix,
        bots.mapValues { it.value.copy() } as MutableMap<Int, Bot>,
        volatile,
        harmonics,
        energy,
        expectedBotActionsThisStep)

    fun addTraceListener(traceListener: TraceListener) {
        traceListeners.add(traceListener)
    }

    fun botIds(): Sequence<Int> = bots.keys.asSequence()

    operator fun get(id: Int): BotView? = bots[id]

    /* Commands */

    fun halt(id: Int) {
        check(bots.count() == 1)
        val bot = checkNotNull(bots[id])
        check(bot.pos == Coord.ZERO)
        check(harmonics == Harmonics.Low)

        bots.remove(id)
        botCommands[id] = Halt
        volatile[bot.pos] = true
    }

    fun wait(id: Int) {
        val bot = checkNotNull(bots[id])
        botCommands[id] = Wait
        volatile[bot.pos] = true
    }

    fun flip(id: Int) {
        val bot = checkNotNull(bots[id])
        harmonics = harmonics.flip()
        botCommands[id] = Flip
        volatile[bot.pos] = true
    }

    fun sMove(id: Int, delta: DeltaCoord) {
        check(delta.isLongLinear)
        val bot = checkNotNull(bots[id])
        val oldPos = bot.pos
        val newPos = oldPos + delta
        check(newPos.isInBounds(matrix.R)) { "out of bounds: $newPos R = ${matrix.R}" }

        volatile[oldPos, newPos] = true
        check(matrix.isVoidRegion(oldPos, newPos))

        bot.pos = newPos
        energy += 2 * delta.mlen
        botCommands[id] = SMove(delta)
    }

    fun lMove(id: Int, delta0: DeltaCoord, delta1: DeltaCoord) {
        check(delta0.isShortLinear)
        check(delta1.isShortLinear)
        val bot = checkNotNull(bots[id])
        val oldPos = bot.pos
        val midPos = oldPos + delta0
        val newPos = midPos + delta1
        check(midPos.isInBounds(matrix.R))
        check(newPos.isInBounds(matrix.R))

        check(matrix.isVoidRegion(oldPos, midPos))
        check(matrix.isVoidRegion(midPos, newPos))
        volatile[oldPos, midPos] = true
        volatile[midPos, newPos] = true

        bot.pos = newPos
        energy += 2 * (delta0.mlen + 2 + delta1.mlen)
        botCommands[id] = LMove(delta0, delta1)
    }

    fun fill(id: Int, delta: DeltaCoord) {
        check(delta.isNear)
        val bot = checkNotNull(bots[id])
        val fillPos = bot.pos + delta
        check(fillPos.isInBounds(matrix.R))

        if (matrix[fillPos]) {
            energy += 6
        } else {
            matrix[fillPos] = true
            energy += 12
        }
        botCommands[id] = Fill(delta)
        volatile[bot.pos] = true
        volatile[fillPos] = true
    }

    fun fission(id: Int, delta: DeltaCoord, m: Int) {
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
        volatile[bot.pos] = true
        volatile[newBotPos] = true
    }

    fun fusion(pId: Int, sId: Int) {
        val pBot = checkNotNull(bots[pId])
        val sBot = checkNotNull(bots[sId])
        check((pBot.pos - sBot.pos).isNear)

        bots.remove(sBot.id)
        pBot.seeds.add(sBot.id)
        pBot.seeds.addAll(sBot.seeds)
        energy -= 24
        botCommands[pId] = FusionP(sBot.pos - pBot.pos)
        botCommands[sId] = FusionS(pBot.pos - sBot.pos)
        volatile[sBot.pos] = true
        volatile[pBot.pos] = true
    }

    fun step() {
        check(botCommands.count() == expectedBotActionsThisStep)

        volatile.clear()

        val volume = matrix.R * matrix.R * matrix.R
        energy += (if (harmonics == Harmonics.High) 30 else 3) * volume
        energy += 20 * expectedBotActionsThisStep

        expectedBotActionsThisStep = bots.count()
        val commands = botCommands.toSortedMap()
        botCommands.clear()

        for (listener in traceListeners) {
            listener.onStep(commands)
        }
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
                    bots == other.bots
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(energy, harmonics, expectedBotActionsThisStep, matrix, bots)
    }

    companion object {
        fun forModel(model: Model): State {
            // TODO: use the model matrix?
            return State(
                Matrix.zerosLike(model.matrix),
                mutableMapOf(1 to Bot(1, Coord.ZERO, (2..20).toSortedSet())))
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
