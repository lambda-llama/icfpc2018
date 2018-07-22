package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.google.common.base.Stopwatch
import io.github.lambdallama.vis.VoxelEngine
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.*
import kotlin.concurrent.thread

fun getStrategy(mode: Mode, model: Model, args: List<String>): Strategy {
    return when (args[0]) {
        "baseline" -> Baseline(mode, model)
        "replay" -> ReplayStrategy(mode, model, File(args[1]))
        "layered" -> LayeredStrategy(mode, model)
        "grounded" -> GroundedStrategy(mode, model)
        "sculptor" -> SculptorStrategy(mode, model)
        "split" -> SplitStrategy(mode, model)
        else -> throw Exception("Invalid strategy name `${args[0]}`")
    }
}

fun getMode(mode: String): Mode {
    return when (mode) {
        "a" -> Mode.Assembly
        "d" -> Mode.Disassembly
        "r" -> Mode.Reassembly
        else -> throw Exception("Unknown mode")
    }
}

fun main(args: Array<String>) {
    val args = (System.getenv("ARGS") ?: "vis").split(" ").toList()
    when (args[0]) {
        "batch" -> {
            val batchFilePath = args[1]
            val strategyArgs = args.drop(2)
            for (line in File(batchFilePath).readLines()) {
                val parts = line.split(" ")
                val mode = getMode(parts[0])
                val modelFilePath = parts[1]
                val traceFilePath = parts[2]
                runNonInteractive(mode, modelFilePath, traceFilePath, strategyArgs)
            }
        }
        "console" -> {
            val mode = getMode(args[1])
            val modelFilePath = args[2]
            val traceFilePath = "out.nbt"
            val strategyArgs = args.drop(3)
            runNonInteractive(mode, modelFilePath, traceFilePath, strategyArgs)
        }
        "vis" -> {
            val mode = if (args.count() > 1) getMode(args[1]) else Mode.Assembly
            val modelFilePath = if (args.count() > 2) args[2] else "problemsF/FA043_tgt.mdl"
            val traceFilePath = "out.nbt"
            val strategyArgs = if (args.count() > 3) args.drop(3) else listOf("grounded")
            runInteractive(mode, modelFilePath, traceFilePath, strategyArgs)
        }
        else -> throw Exception("Invalid execution mode")
    }
}

private fun createStrategy(mode: Mode, modelFilePath: String, traceFilePath: String, args: List<String>): Strategy {
    val model = Model.parse(File(modelFilePath))
    val traceOutputStream = DataOutputStream(File(traceFilePath).outputStream().buffered())
    val strategy = getStrategy(mode, model, args)
    strategy.state.addTraceListener(TraceWriter(traceOutputStream))
    return strategy
}

private fun runNonInteractive(mode: Mode, modelFilePath: String, traceFilePath: String, args: List<String>) {
    when (mode) {
        Mode.Assembly -> print("Assembling ")
        Mode.Disassembly -> print("Disassembling ")
        Mode.Reassembly -> print("Reassembling ")
    }
    print("${modelFilePath}... ")
    try {
        val strategy = createStrategy(mode, modelFilePath, traceFilePath, args)
        val sw: Stopwatch = Stopwatch.createStarted()
        for (state in strategy.run()) {
            // do nothing
        }
        sw.stop()
        println("OK (${strategy.state.energy} energy, in ${sw.elapsed(TimeUnit.MILLISECONDS)}ms)")
    } catch (e: Exception) {
        println("EXCEPTION (${e.message}")
        e.printStackTrace()
    }
}

private fun runInteractive(mode: Mode, modelFilePath: String, traceFilePath: String, args: List<String>) {
    val strategy = createStrategy(mode, modelFilePath, traceFilePath, args)
    val currentState = CurrentState(strategy.state)
    val engine = VoxelEngine(strategy.name, currentState)
    strategy.state.addTraceListener(currentState)
    thread {
        for (state in strategy.run()) {
            currentState.setState(state)
            if (currentState.step) {
                currentState.step = false
                continue
            }
            Thread.sleep(currentState.delayMs.toLong())
            while (currentState.paused) {
                if (currentState.step) break
                Thread.sleep(100)
            }
        }
        println("Total energy: " + strategy.state.energy)
    }
    LwjglApplication(engine, LwjglApplicationConfiguration().apply {
        forceExit = false
        width = 1024
        height = 768
    })
}

data class Snapshot(
        val state: State,
        val lastCommands: SortedMap<Int, Command> = TreeMap(),
        val totalSteps: Int = 0
)

data class CurrentState(
        @Volatile var snapshot: Snapshot,
        @Volatile var delayMs: Double = 250.0,
        @Volatile var paused: Boolean = true,
        @Volatile var step: Boolean = false
) : TraceListener {
    constructor(state: State) : this(Snapshot(state))

    override fun onStep(commands: SortedMap<Int, Command>) = updateSnapshot { old ->
        old.copy(lastCommands = commands, totalSteps = old.totalSteps + 1)
    }

    fun setState(state: State) = updateSnapshot { old -> old.copy(state = state.split()) }

    private fun updateSnapshot(f: (Snapshot) -> Snapshot) {
        synchronized(this) {
            snapshot = f(snapshot)
        }
    }
}
