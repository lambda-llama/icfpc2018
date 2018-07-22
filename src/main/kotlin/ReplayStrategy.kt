package io.github.lambdallama

import java.io.DataInputStream
import java.io.File
import kotlin.coroutines.experimental.buildSequence

class ReplayStrategy(val mode: Mode, val model: Model, source: Model?, trace: File) : Strategy {
    private val commands = TraceReader(DataInputStream(trace.inputStream().buffered())).readAllCommands()
    override val name: String = "Replay (${trace.path})"
    override val state: State = State.create(mode, model.matrix, source?.matrix)

    override fun run(): Sequence<State> = buildSequence {
        yield(state)
        val iter = commands.iterator()

        var stop = false
        while (!stop) {
            for (id in state.botIds().sorted().toList()) {
                val command = iter.next()
                val fusions = HashMap<Int, Int>()
                when (command) {
                    is Halt -> {
                        state.halt(id)
                        stop = true
                    }
                    is Wait -> state.wait(id)
                    is Flip -> state.flip(id)
                    is SMove -> state.sMove(id, command.delta)
                    is LMove -> state.lMove(id, command.delta0, command.delta1)
                    is Fill -> state.fill(id, command.delta)
                    is Fission -> state.fission(id, command.delta, command.m)
                    is FusionP -> {
                        if (fusions.containsKey(id)) {
                            state.fusion(id, fusions[id]!!)
                        } else {
                            val sBotPos = state[id]!!.pos + command.delta
                            val sBotId = state.botIds()
                                    .map { id -> state[id]!! }
                                    .single { b -> b.pos == sBotPos }
                                    .id
                            fusions[sBotId] = id
                        }
                    }
                    is FusionS -> {
                        if (fusions.containsKey(id)) {
                            state.fusion(fusions[id]!!, id)
                        } else {
                            val pBotPos = state[id]!!.pos + command.delta
                            val pBotId = state.botIds()
                                    .map { id -> state[id]!! }
                                    .single { b -> b.pos == pBotPos }
                                    .id
                            fusions[pBotId] = id
                        }
                    }
                }
                state.step()
                yield(state)
            }
        }
    }
}
