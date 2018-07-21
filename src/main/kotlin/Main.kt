package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.google.common.base.Stopwatch
import io.github.lambdallama.vis.VoxelEngine
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun getStrategy(args: List<String>, model: Model): Strategy {
    return when (args[0]) {
        "baseline" -> Baseline(model)
        "replay" -> ReplayStrategy(model, File(args[1]))
        "layered" -> LayeredStrategy(model)
        else -> throw Exception("Invalid strategy name")
    }
}

fun main(args: Array<String>) {
    var args = (System.getenv("ARGS") ?: "baseline").split(" ").toList()

    if (args[0] == "batch") {
        val batchFilePath = args[1]
        args = args.drop(2)
        for (line in File(batchFilePath).readLines()) {
            val parts = line.split(" ")
            val modelFilePath = parts[0]
            val traceFilePath = parts[1]
            runNonInteractive(modelFilePath, traceFilePath, args)
        }
    } else {
        runInteractive("problemsL/LA017_tgt.mdl", "out.nbt", args)
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
    val engine = VoxelEngine(strategy.name, strategy.state)
    strategy.state.addTraceListener(engine)
    thread {
        for (state in strategy.run()) {
            engine.update(state)
        }

        println("Total energy: " + strategy.state.energy)
    }
    LwjglApplication(engine, LwjglApplicationConfiguration().apply {
        forceExit = false
        width = 1024
        height = 768
    })
}
