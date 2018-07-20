package io.github.lambdallama

interface Strategy {
    val state: State

    fun run(): Sequence<State>
}
