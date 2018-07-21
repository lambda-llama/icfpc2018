package io.github.lambdallama

class State(
    val matrix: Matrix,
    val bots: MutableMap<Int, Bot>
) {
    private val traceListeners: ArrayList<TraceListener> = ArrayList()

    private val botCommands: HashMap<Int, Command> = HashMap()
    private var expectedBotActionsThisStep: Int = bots.count()
    var harmonics: Harmonics = Harmonics.Low
        private set
    var energy: Long = 0
        private set

    /* Private methods */

    /* General public methods */

    fun addTraceListener(traceListener: TraceListener) {
        traceListeners.add(traceListener)
    }

    fun botIds(): Sequence<Int> {
        return bots.keys.asSequence()
    }

    fun getBot(id: Int): BotView {
        return bots[id] as BotView
    }

    /* Commands */

    fun halt(id: Int) {
        assert(bots.contains(id))
        assert(bots.count() == 1)
        val bot = bots[id]!!
        assert(bot.pos == Coord.ZERO)
        assert(harmonics == Harmonics.Low)

        bots.remove(id)
        botCommands[id] = Halt
    }

    fun wait(id: Int) {
        assert(bots.contains(id))

        botCommands[id] = Wait
    }

    fun flip(id: Int) {
        assert(bots.contains(id))

        harmonics = harmonics.flip()
        botCommands[id] = Flip
    }

    fun sMove(id: Int, delta: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta.isLongLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val newPos = oldPos + delta
        assert(newPos.isInBounds(matrix.R))

        matrix.forEach(oldPos, newPos) { x, y, z -> assert(!matrix[x, y, z]) }

        bot.pos = newPos
        energy += 2 * delta.mlen
        botCommands[id] = SMove(delta)
    }

    fun lMove(id: Int, delta0: DeltaCoord, delta1: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta0.isShortLinear)
        assert(delta1.isShortLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val midPos = oldPos + delta0
        val newPos = midPos + delta1
        assert(midPos.isInBounds(matrix.R))
        assert(newPos.isInBounds(matrix.R))

        matrix.forEach(oldPos, midPos) { x, y, z -> assert(!matrix[x, y, z]) }
        matrix.forEach(midPos, newPos) { x, y, z -> assert(!matrix[x, y, z]) }

        bot.pos = newPos
        energy += 2 * (delta0.mlen + 2 + delta1.mlen)
        botCommands[id] = LMove(delta0, delta1)
    }

    fun fill(id: Int, delta: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta.isNear)
        val bot = bots[id]!!
        val fillPos = bot.pos + delta
        assert(fillPos.isInBounds(matrix.R))

        if (matrix[fillPos]) {
            energy += 6
        } else {
            matrix[fillPos] = true
            energy += 12
        }
        botCommands[id] = Fill(delta)
    }

    fun fission(id: Int, delta: DeltaCoord, m: Int) {
        assert(bots.contains(id))
        assert(delta.isNear)
        val bot = bots[id]!!
        assert(bot.seeds.any())
        val newBotPos = bot.pos + delta
        assert(newBotPos.isInBounds(matrix.R))
        assert(!matrix[newBotPos])
        assert(m < bot.seeds.count())

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
        assert(bots.contains(pId))
        assert(bots.contains(sId))
        val pBot = bots[pId]!!
        val sBot = bots[sId]!!
        assert((pBot.pos - sBot.pos).isNear)

        bots.remove(sBot.id)
        pBot.seeds.add(sBot.id)
        pBot.seeds.addAll(sBot.seeds)
        energy -= 24
        botCommands[pId] = FusionP(sBot.pos - pBot.pos)
        botCommands[sId] = FusionS(pBot.pos - sBot.pos)
    }

    fun step() {
        assert(botCommands.count() == expectedBotActionsThisStep)

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
