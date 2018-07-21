package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import java.io.DataOutputStream
import java.io.File
import kotlin.concurrent.thread

fun getStrategy(args: List<String>, model: Model): Strategy {
    return when (args[0]) {
        "baseline" -> Baseline(model)
        "replay" -> ReplayStrategy(model, File(args[1]))
        else -> throw Exception("Invalid strategy name")
    }
}

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply {
        forceExit = false
        width = 1024
        height = 768
    }
    val model = Model.parse(File("problemsL/LA017_tgt.mdl"))
    val traceOutputStream = DataOutputStream(File("out.nbt").outputStream().buffered())
    val engine = VoxelEngine(model)
    thread {
        val strategy = getStrategy(System.getenv("ARGS").split(" "), model)
        strategy.state.addTraceListener(TraceWriter(traceOutputStream))
        var last = strategy.state
        for (state in strategy.run()) {
            engine.updateModel(model.copy(matrix = state.matrix))
            Thread.sleep(10)
            last = state
        }

        println("Total energy: " + last.energy)
    }
    LwjglApplication(engine, config)
}
