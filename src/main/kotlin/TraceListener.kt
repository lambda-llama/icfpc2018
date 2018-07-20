package io.github.lambdallama

import java.util.*

interface TraceListener {
    fun onStep(commands: SortedMap<Int, Command>)
}
