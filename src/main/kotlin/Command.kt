package io.github.lambdallama

sealed class Command

object Halt : Command() {
    override fun toString(): String {
        return "Halt()"
    }
}

object Wait : Command() {
    override fun toString(): String {
        return "Wait()"
    }
}

object Flip : Command() {
    override fun toString(): String {
        return "Flip()"
    }
}

data class SMove(val delta: DeltaCoord): Command() {
    override fun toString(): String {
        return "SMove(${delta})"
    }
}

data class LMove(val delta0: DeltaCoord, val delta1: DeltaCoord): Command() {
    override fun toString(): String {
        return "LMove(${delta0}, ${delta1})"
    }
}

data class Fill(val delta: DeltaCoord): Command() {
    override fun toString(): String {
        return "Fill(${delta})"
    }
}

data class Fission(val delta: DeltaCoord, val m: Int): Command() {
    override fun toString(): String {
        return "Fission(${delta}, ${m})"
    }
}

data class FusionP(val delta: DeltaCoord): Command() {
    override fun toString(): String {
        return "FusionP(${delta})"
    }
}

data class FusionS(val delta: DeltaCoord): Command() {
    override fun toString(): String {
        return "FusionS(${delta})"
    }
}
