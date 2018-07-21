package io.github.lambdallama

interface Strategy {
    val name: String
    val state: State

    fun run(): Sequence<State>
}
