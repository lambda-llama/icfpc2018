package io.github.lambdallama

class State {
    companion object {
        enum class Harmonics {
            Low,
            High,
        }
    }

    private val traceListeners: ArrayList<TraceListener> = ArrayList()

    private val bots: HashMap<Int, Bot> = HashMap()
    private val actedBots: HashSet<Int> = HashSet()
    private var expectedBotActionsThisStep: Int = 0
    var resolution: Coord = Coord.ZERO
    var harmonics: Harmonics = Harmonics.Low
        private set
    var energy: Long = 0
        private set

    /* Private methods */

    private fun mlen(dx: Int, dy: Int, dz: Int): Int {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz)
    }

    /* General public methods */

    fun loadFrom(/* */) {
        // TODO: check if below is correct
        harmonics = Harmonics.Low
        energy = 0

        // TODO: load

        expectedBotActionsThisStep = bots.count()
    }

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
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onHalt(this, id)
        }
    }

    fun wait(id: Int) {
        assert(bots.contains(id))

        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onWait(this, id)
        }
    }

    fun flip(id: Int) {
        assert(bots.contains(id))

        harmonics = if (harmonics == Harmonics.Low) Harmonics.High else Harmonics.Low
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onFlip(this, id)
        }
    }

    fun sMove(id: Int, delta: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta.isLongLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val newPos = oldPos + delta
        assert(newPos.isInBounds(resolution))
        // TODO: assert region

        bot.pos = newPos
        energy += 2 * delta.mlen
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onSMove(this, id, oldPos)
        }
    }

    fun lMove(id: Int, delta0: DeltaCoord, delta1: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta0.isShortLinear)
        assert(delta1.isShortLinear)
        val bot = bots[id]!!
        val oldPos = bot.pos
        val midPos = oldPos + delta0
        val newPos = midPos + delta1
        assert(midPos.isInBounds(resolution))
        assert(newPos.isInBounds(resolution))
        // TODO: assert region

        bot.pos = newPos
        energy += 2 * (delta0.mlen + 2 + delta1.mlen)
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onLMove(this, id, oldPos, midPos)
        }
    }

    fun fill(id: Int, delta: DeltaCoord) {
        assert(bots.contains(id))
        assert(delta.isNear)
        val bot = bots[id]!!
        val fillPos = bot.pos + delta
        assert(fillPos.isInBounds(resolution))

        // TODO: fill in voxel
        // TODO: check if full, add only 6 in that case
        energy += 12
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onFill(this, id, fillPos)
        }
    }

    fun fission(id: Int, delta: DeltaCoord, m: Int) {
        assert(bots.contains(id))
        assert(delta.isNear)
        val bot = bots[id]!!
        val newBotPos = bot.pos + delta
        assert(newBotPos.isInBounds(resolution))
        // TODO: check that new position is not Full
        // TODO: assert m

        // TODO: action
        energy += 24
        actedBots.add(id)

        for (listener in traceListeners) {
            listener.onFission(this, id, m)
        }
    }

    fun fusion(pId: Int, sId: Int) {
        assert(bots.contains(pId))
        assert(bots.contains(sId))
        val pBot = bots[pId]!!
        val sBot = bots[sId]!!
        val sPos = sBot.pos
        assert((pBot.pos - sBot.pos).isNear)

        // TODO: action
        energy -= 24
        actedBots.add(pId)
        actedBots.add(sId)

        for (listener in traceListeners) {
            // TODO: supply pBot/sBot coords
            listener.onFusion(this, pId, sId, sPos)
        }
    }

    fun step() {
        assert(actedBots.count() == expectedBotActionsThisStep)

        expectedBotActionsThisStep = bots.count()
        actedBots.clear()
        energy += if (harmonics == Harmonics.High) 30 * resolution.volume else 3 * resolution.volume

        for (listener in traceListeners) {
            listener.onStep()
        }
    }
}
