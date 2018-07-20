package io.github.lambdallama

import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import io.github.lambdallama.vis.VoxelEngine
import vis.VoxelEngine

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply { forceExit = false }
    LwjglApplication(VoxelEngine(), config)
}
