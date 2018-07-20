package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import java.io.File

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply { forceExit = false }
    val model = Model.parse(File("problemsL/LA001_tgt.mdl"))
    val engine = VoxelEngine(model)
    LwjglApplication(engine, config)
}
