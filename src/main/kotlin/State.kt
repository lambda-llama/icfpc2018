package lambdallama.github.io

class State {
    private val traceListeners: ArrayList<TraceListener> = ArrayList()

    var energy: Long = 0
        private set

    /* Private methods */

    private fun mlen(dx: Int, dy: Int, dz: Int): Int {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz)
    }

    /* General public methods */

    fun loadFrom(/* */) { }

    fun addTraceListener(traceListener: TraceListener) {
        traceListeners.add(traceListener)
    }

    /* Commands */
    fun halt(bot: Int) {
        // TODO: action

        for (listener in traceListeners) {
            listener.onHalt(this, bot)
        }
    }

    fun wait(bot: Int) {
        // TODO: action

        for (listener in traceListeners) {
            listener.onWait(this, bot)
        }
    }

    fun flip(bot: Int) {
        // TODO: action

        for (listener in traceListeners) {
            listener.onFlip(this, bot)
        }
    }

    fun sMove(bot: Int, dx: Int, dy: Int, dz: Int) {
        energy += 2 * mlen(dx, dy, dz)

        // TODO: action

        for (listener in traceListeners) {
            listener.onSMove(this, bot, dx, dy, dz)
        }
    }

    fun lMove(bot: Int, dx0: Int, dy0: Int, dz0: Int, dx1: Int, dy1: Int, dz1: Int) {
        energy += 2 * (mlen(dx0, dy0, dz0) + 2 + mlen(dx1, dy1, dz1))

        // TODO: action

        for (listener in traceListeners) {
            listener.onLMove(this, bot, dx0, dy0, dz0, dx1, dy1, dz1)
        }
    }

    fun fill(bot: Int, dx: Int, dy: Int, dz: Int) {
        // TODO: check if full, add only 6 in that case
        energy += 12

        // TODO: action

        for (listener in traceListeners) {
            listener.onFill(this, bot, dx, dy, dz)
        }
    }

    fun fission(bot: Int, dx: Int, dy: Int, dz: Int, m: Int) {
        energy += 24

        // TODO: action

        for (listener in traceListeners) {
            listener.onFission(this, bot, dx, dy, dz, m)
        }
    }

    fun fusion(pBot: Int, sBot: Int) {
        energy -= 24

        // TODO: action

        for (listener in traceListeners) {
            // TODO: supply pBot/sBot coords
            listener.onFusion(this, pBot, sBot, 0, 0, 0 ,0 ,0 ,0)
        }
    }

    fun step() {
        // TODO: change energy

        // TODO: action

        for (listener in traceListeners) {
            listener.onStep()
        }
    }
}
