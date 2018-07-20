interface TraceListener {
    fun onHalt(state: State, bot: Int)
    fun onWait(state: State, bot: Int)
    fun onFlip(state: State, bot: Int)
    fun onSMove(state: State, bot: Int, dx: Int, dy: Int, dz: Int)
    fun onLMove(state: State, bot: Int, dx0: Int, dy0: Int, dz0: Int, dx1: Int, dy1: Int, dy2: Int)
    fun onFill(state: State, bot: Int, dx: Int, dy: Int, dz: Int)
    fun onFission(state: State, bot: Int, dx: Int, dy: Int, dz: Int, m: Int)
    fun onFusion(state: State, pBot: Int, sBot: Int, pX: Int, pY: Int, pZ: Int,
                 sX: Int, sY: Int, sZ: Int)
    fun onStep()
}
