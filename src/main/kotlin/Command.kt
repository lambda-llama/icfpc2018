package io.github.lambdallama

sealed class Command {
    abstract operator fun invoke(state: State, id: Int)
}

object Halt : Command() {
    override fun invoke(state: State, id: Int) = state.halt(id)

    override fun toString() = "Halt()"
}

object Wait : Command() {
    override fun invoke(state: State, id: Int) = state.wait(id)

    override fun toString() = "Wait()"
}

object Flip : Command() {
    override fun invoke(state: State, id: Int) = state.flip(id)

    override fun toString() = "Flip()"
}

data class SMove(val delta: DeltaCoord): Command() {
    override fun invoke(state: State, id: Int) = state.sMove(id, delta)

    override fun toString() = "SMove($delta)"
}

data class LMove(val delta0: DeltaCoord, val delta1: DeltaCoord): Command() {
    override fun invoke(state: State, id: Int) = state.lMove(id, delta0, delta1)

    override fun toString() = "LMove($delta0, $delta1)"
}

data class Fill(val delta: DeltaCoord): Command() {
    override fun invoke(state: State, id: Int) = state.fill(id, delta)

    override fun toString() = "Fill($delta)"
}

data class Fission(val delta: DeltaCoord, val m: Int): Command() {
    override fun invoke(state: State, id: Int) = state.fission(id, delta, m)

    override fun toString() = "Fission($delta, $m)"
}

data class FusionP(val delta: DeltaCoord): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "FusionP($delta)"
}

data class FusionS(val delta: DeltaCoord): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "FusionS($delta)"
}
