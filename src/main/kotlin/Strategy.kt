package io.github.lambdallama

interface Strategy {
    fun run(model: Model): Sequence<State>
}
