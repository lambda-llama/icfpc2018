package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import org.omg.PortableServer.THREAD_POLICY_ID
import java.io.File
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply {
        forceExit = false
        width = 1024
        height = 768
    }
    val model = Model.parse(File("problemsL/LA017_tgt.mdl"))
    val engine = VoxelEngine(model)
    thread {
        for (state in baseline(model)) {
            engine.updateModel(model.copy(matrix = state.matrix))
            Thread.sleep(300)
        }
    }
    LwjglApplication(engine, config)
}
