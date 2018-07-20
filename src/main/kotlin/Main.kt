package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import java.io.File

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply {
        forceExit = false
        width = 1024
        height = 768
    }
    val model = Model.parse(File("problemsL/LA017_tgt.mdl"))
    val state = baseline(model)
    println("Energy: " + state.energy)
    val engine = VoxelEngine(model.copy(matrix = state.matrix))
    LwjglApplication(engine, config)
}
