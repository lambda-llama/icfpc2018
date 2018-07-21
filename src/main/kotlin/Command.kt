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

data class SMove(val delta: Delta): Command() {
    override fun invoke(state: State, id: Int) = state.sMove(id, delta)

    override fun toString() = "SMove($delta)"
}

data class LMove(val delta0: Delta, val delta1: Delta): Command() {
    override fun invoke(state: State, id: Int) = state.lMove(id, delta0, delta1)

    override fun toString() = "LMove($delta0, $delta1)"
}

data class Fill(val delta: Delta): Command() {
    override fun invoke(state: State, id: Int) = state.fill(id, delta)

    override fun toString() = "Fill($delta)"
}

data class Void(val delta: Delta): Command() {
    override fun invoke(state: State, id: Int) = state.void(id, delta)

    override fun toString() = "Void($delta)"
}

data class Fission(val delta: Delta, val m: Int): Command() {
    override fun invoke(state: State, id: Int) { state.fission(id, delta, m) }

    override fun toString() = "Fission($delta, $m)"
}

data class FusionP(val delta: Delta): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "FusionP($delta)"
}

data class FusionS(val delta: Delta): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "FusionS($delta)"
}

data class GFill(val dNear: Delta, val dFar: Delta): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "GFill($dNear, $dFar)"
}

data class GVoid(val dNear: Delta, val dFar: Delta): Command() {
    override fun invoke(state: State, id: Int) = TODO()

    override fun toString() = "GVoid($dNear, $dFar)"
}
