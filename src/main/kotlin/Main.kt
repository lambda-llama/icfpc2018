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

fun getStrategy(args: List<String>, model: Model): Strategy {
    return when (args[0]) {
        "baseline" -> Baseline(model)
        "replay" -> ReplayStrategy(model, File(args[1]))
        "layered" -> LayeredStrategy(model)
        "grounded" -> GroundedStrategy(model)
        else -> throw Exception("Invalid strategy name `${args[0]}`")
    }
}

fun main(args: Array<String>) {
    var args = (System.getenv("ARGS") ?: "layered").split(" ").toList()
    when {
        args[0] == "batch" -> {
            val batchFilePath = args[1]
            args = args.drop(2)
            for (line in File(batchFilePath).readLines()) {
                val parts = line.split(" ")
                val modelFilePath = parts[0]
                val traceFilePath = parts[1]
                runNonInteractive(modelFilePath, traceFilePath, args)
            }
        }
        args[0] == "console" -> {
            val modelFilePath = args[1]
            val traceFilePath = "out.nbt"
            args = args.drop(2)
            runNonInteractive(modelFilePath, traceFilePath, args)
        }
        else -> runInteractive("problemsF/FA043_tgt.mdl", "out.nbt", listOf("grounded"))
    }
}

private fun createStrategy(modelFilePath: String, traceFilePath: String, args: List<String>): Strategy {
    val model = Model.parse(File(modelFilePath))
    val traceOutputStream = DataOutputStream(File(traceFilePath).outputStream().buffered())
    val strategy = getStrategy(args, model)
    strategy.state.addTraceListener(TraceWriter(traceOutputStream))
    return strategy
}

private fun runNonInteractive(modelFilePath: String, traceFilePath: String, args: List<String>) {
    print("Solving ${modelFilePath}... ")
    try {
        val strategy = createStrategy(modelFilePath, traceFilePath, args)
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

private fun runInteractive(modelFilePath: String, traceFilePath: String, args: List<String>) {
    val strategy = createStrategy(modelFilePath, traceFilePath, args)
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
        @Volatile var paused: Boolean = false,
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
