package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import java.io.File

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply { forceExit = false }
    val engine = VoxelEngine()
    val m = Model.parse(File("problemsL/LA001_tgt.mdl"))
    println(m)
    engine.setModel(m)
    LwjglApplication(engine, config)
}
