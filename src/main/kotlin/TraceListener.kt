package io.github.lambdallama

interface TraceListener {
    fun onHalt(state: State, id: Int)
    fun onWait(state: State, id: Int)
    fun onFlip(state: State, id: Int)
    fun onSMove(state: State, id: Int, oldPos: Coord)
    fun onLMove(state: State, id: Int, oldPos: Coord, midPos: Coord)
    fun onFill(state: State, id: Int, fillPos: Coord)
    fun onFission(state: State, id: Int, m: Int)
    fun onFusion(state: State, pId: Int, sId: Int, sPos: Coord)
    fun onStep()
}
