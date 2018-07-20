package vis

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Vector3
import com.brzez.voxelengine.chunk.ChunkData
import com.brzez.voxelengine.chunk.ChunkModelBuilder

class VoxelEngine : ApplicationAdapter() {
    lateinit var camera: PerspectiveCamera
    lateinit var chunkModel: Model
    lateinit var instance: ModelInstance
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

        chunkModel = createChunkModel()
        instance = ModelInstance(chunkModel)
    }

    private fun createChunkModel(): Model {
        val d = ChunkData(16)
        val halfSize = (d.size / 2).toFloat()
        // fill the chunk data w/ some random crap
        var x = d.size.toInt()
        while (x-- > 0) {
            var y = d.size.toInt()
            while (y-- > 0) {
                var z = d.size.toInt()
                while (z-- > 0) {
                    val len = Vector3(halfSize - x, halfSize - y, halfSize - z)
                    if (len.len() / d.size > .4f) {
                        continue
                    }
                    d.set(x, y, z, 1.toByte())
                }
            }
        }
        return ChunkModelBuilder().build(d)
    }


    override fun render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        keyboardControls()

        modelBatch.begin(camera)
        modelBatch.render(instance, environment)
        modelBatch.end()
    }

    private fun keyboardControls() {
        val speed = 2f

        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            instance.transform.rotate(0f, 1f, 0f, speed)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            instance.transform.rotate(0f, 1f, 0f, -speed)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            instance.transform.rotate(1f, 0f, 0f, speed)
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            instance.transform.rotate(1f, 1f, 0f, -speed)
        }

    }

    override fun dispose() {
        chunkModel.dispose()
    }
}
