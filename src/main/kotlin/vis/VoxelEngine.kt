package io.github.lambdallama.vis

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import io.github.lambdallama.Matrix
import vis.floorModel
import vis.toVisModel
import java.io.File

class VoxelEngine(private val model: io.github.lambdallama.Model) : ApplicationAdapter() {
    lateinit var camera: PerspectiveCamera
    private var chunkModel: Model? = null
    private var instance: ModelInstance? = null
    lateinit var floorModel: Model
    lateinit var floorInstance: ModelInstance
    lateinit var modelBatch: ModelBatch
    lateinit var environment: Environment

    override fun create() {
        modelBatch = ModelBatch()

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(10f, 10f, 10f)
        camera.lookAt(0f, 0f, 0f)
        camera.near = 1f
        camera.far = 300f
        camera.update()

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        chunkModel = model.toVisModel()
        instance = ModelInstance(chunkModel)

        floorModel = model.floorModel()
        floorInstance = ModelInstance(floorModel)
    }

    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        keyboardControls()

        modelBatch.begin(camera)
        instance?.let { modelBatch.render(it, environment) }
        modelBatch.render(floorInstance, environment)
        modelBatch.end()
    }

    private fun keyboardControls() {
        val speed = 2f
        val instance = instance
        if (instance != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
                instance.transform.scale(1.1f, 1.1f, 1.1f)
                floorInstance.transform.scale(1.1f, 1.1f, 1.1f)
            }

            if (Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
                instance.transform.scale(0.9f, 0.9f, 0.9f)
                floorInstance.transform.scale(0.9f, 0.9f, 0.9f)
            }

            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                instance.transform.rotate(0f, 1f, 0f, speed)
                floorInstance.transform.rotate(0f, 1f, 0f, speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                instance.transform.rotate(0f, 1f, 0f, -speed)
                floorInstance.transform.rotate(0f, 1f, 0f, -speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
                instance.transform.rotate(1f, 0f, 0f, speed)
                floorInstance.transform.rotate(1f, 0f, 0f, speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
                instance.transform.rotate(1f, 1f, 0f, -speed)
                floorInstance.transform.rotate(1f, 1f, 0f, -speed)
            }
        }
    }

    override fun dispose() {
        chunkModel?.dispose()
        floorModel.dispose()
    }
}
