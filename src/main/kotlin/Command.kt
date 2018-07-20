package io.github.lambdallama

sealed class Command
object Halt : Command()
object Wait : Command()
object Flip : Command()
data class SMove(val delta: DeltaCoord): Command()
data class LMove(val delta0: DeltaCoord, val delta1: DeltaCoord): Command()
data class Fill(val delta: DeltaCoord): Command()
data class Fission(val delta: DeltaCoord, val m: Int): Command()
data class FusionP(val delta: DeltaCoord): Command()
data class FusionS(val delta: DeltaCoord): Command()
