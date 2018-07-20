package lambdallama.github.io

class State {
    private val traceListeners: ArrayList<TraceListener> = ArrayList()

    fun loadFrom(/* */) { }

    fun addTraceListener(traceListener: TraceListener) {
        traceListeners.add(traceListener)
    }

    /* Commands */
    fun halt(bot: Int) {
        for (listener in traceListeners) {
            listener.onHalt(this, bot)
        }
    }

    fun wait(bot: Int) {
        for (listener in traceListeners) {
            listener.onWait(this, bot)
        }
    }

    fun flip(bot: Int) {
        for (listener in traceListeners) {
            listener.onFlip(this, bot)
        }
    }

    fun sMove(bot: Int, dx: Int, dy: Int, dz: Int) {
        for (listener in traceListeners) {
            listener.onSMove(this, bot, dx, dy, dz)
        }
    }

    fun lMove(bot: Int, dx0: Int, dy0: Int, dz0: Int, dx1: Int, dy1: Int, dz1: Int) {
        for (listener in traceListeners) {
            listener.onLMove(this, bot, dx0, dy0, dz0, dx1, dy1, dz1)
        }
    }

    fun fill(bot: Int, dx: Int, dy: Int, dz: Int) {
        for (listener in traceListeners) {
            listener.onFill(this, bot, dx, dy, dz)
        }
    }

    fun fission(bot: Int, dx: Int, dy: Int, dz: Int, m: Int) {
        for (listener in traceListeners) {
            listener.onFission(this, bot, dx, dy, dz, m)
        }
    }

    fun fusion(pBot: Int, sBot: Int) {
        for (listener in traceListeners) {
            // TODO: supply pBot/sBot coords
            listener.onFusion(this, pBot, sBot, 0, 0, 0 ,0 ,0 ,0)
        }
    }
}
