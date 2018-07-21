package io.github.lambdallama

import java.util.*

class State(
    val matrix: Matrix,
    val bots: MutableMap<Int, Bot>,
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
        harmonics,
        energy,
        expectedBotActionsThisStep)

    fun shallowSplit() = State(
        matrix,
        bots.mapValues { it.value.copy() } as MutableMap<Int, Bot>,
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
        check(bots.contains(id))
        check(bots.count() == 1)
        val bot = bots[id]!!
        check(bot.pos == Coord.ZERO)
        check(harmonics == Harmonics.Low)

        bots.remove(id)
        botCommands[id] = Halt
    }

    fun wait(id: Int) {
        check(bots.contains(id))

        botCommands[id] = Wait
    }

    fun flip(id: Int) {
        check(bots.contains(id))

        harmonics = harmonics.flip()
        botCommands[id] = Flip
    }

    fun sMove(id: Int, delta: DeltaCoord) {
        check(bots.contains(id))
        check(delta.isLongLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val newPos = oldPos + delta
        check(newPos.isInBounds(matrix.R)) { "out of bounds: $newPos R = ${matrix.R}" }

        assert(matrix.isVoidRegion(oldPos, newPos))

        bot.pos = newPos
        energy += 2 * delta.mlen
        botCommands[id] = SMove(delta)
    }

    fun lMove(id: Int, delta0: DeltaCoord, delta1: DeltaCoord) {
        check(bots.contains(id))
        check(delta0.isShortLinear)
        check(delta1.isShortLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val midPos = oldPos + delta0
        val newPos = midPos + delta1
        check(midPos.isInBounds(matrix.R))
        check(newPos.isInBounds(matrix.R))

        assert(matrix.isVoidRegion(oldPos, midPos))
        assert(matrix.isVoidRegion(midPos, newPos))

        bot.pos = newPos
        energy += 2 * (delta0.mlen + 2 + delta1.mlen)
        botCommands[id] = LMove(delta0, delta1)
    }

    fun fill(id: Int, delta: DeltaCoord) {
        check(bots.contains(id))
        check(delta.isNear)
        val bot = bots[id]!!
        val fillPos = bot.pos + delta
        check(fillPos.isInBounds(matrix.R))

        if (matrix[fillPos]) {
            energy += 6
        } else {
            matrix[fillPos] = true
            energy += 12
        }
        botCommands[id] = Fill(delta)
    }

    fun fission(id: Int, delta: DeltaCoord, m: Int) {
        check(bots.contains(id))
        check(delta.isNear)
        val bot = bots[id]!!
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
    }

    fun fusion(pId: Int, sId: Int) {
        check(bots.contains(pId))
        check(bots.contains(sId))
        val pBot = bots[pId]!!
        val sBot = bots[sId]!!
        check((pBot.pos - sBot.pos).isNear)

        bots.remove(sBot.id)
        pBot.seeds.add(sBot.id)
        pBot.seeds.addAll(sBot.seeds)
        energy -= 24
        botCommands[pId] = FusionP(sBot.pos - pBot.pos)
        botCommands[sId] = FusionS(pBot.pos - sBot.pos)
    }

    fun step() {
        check(botCommands.count() == expectedBotActionsThisStep)

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
