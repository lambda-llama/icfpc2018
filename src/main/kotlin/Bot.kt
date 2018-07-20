package io.github.lambdallama

import java.util.*

interface BotView {
    val id: Int
    val pos: Coord

    fun seeds(): Sequence<Int>
}

data class Bot(
    override val id: Int,
    override var pos: Coord,
    var seeds: SortedSet<Int>)
    : BotView {

    override fun seeds(): Sequence<Int> {
        return seeds.asSequence()
    }
}
