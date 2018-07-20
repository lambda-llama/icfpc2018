package io.github.lambdallama

interface BotView {
    val id: Int
    val pos: Coord
}

data class Bot(
    override val id: Int,
    override var pos: Coord)
    : BotView
