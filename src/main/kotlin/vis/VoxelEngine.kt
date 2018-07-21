package io.github.lambdallama.vis

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import io.github.lambdallama.State
import vis.floorModel
import vis.toVisModel
import com.badlogic.gdx.utils.viewport.ScreenViewport
import io.github.lambdallama.Command
import io.github.lambdallama.TraceListener
import java.util.*


class VoxelEngine(private val strategyName: String, private var state: State)
    : ApplicationAdapter(), TraceListener {
    lateinit var camera: PerspectiveCamera
    private var chunkModel: Model? = null
    private var instance: ModelInstance? = null
    lateinit var floorModel: Model
    lateinit var floorInstance: ModelInstance
    lateinit var modelBatch: ModelBatch
    lateinit var environment: Environment
    private var sleepTimeMs: Long = 250
    private var paused: Boolean = true
    private var step: Boolean = false
    private var stage: Stage? = null
    private var info: Label? = null
    private var lastCommands: SortedMap<Int, Command> = TreeMap()
    private var totalSteps: Int = 0

    override fun create() {
        modelBatch = ModelBatch()

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(10f, 10f, 10f)
        camera.lookAt(0f, 0f, 0f)
        camera.near = 1f
        camera.far = 300f
        camera.update()

        stage = Stage(ScreenViewport())

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        chunkModel = state.toVisModel()
        instance = ModelInstance(chunkModel)

        floorModel = state.floorModel()
        floorInstance = ModelInstance(floorModel)

        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/ubuntu.ttf"))
        val param = FreeTypeFontGenerator.FreeTypeFontParameter()
        param.size = 16
        param.color = Color.LIGHT_GRAY
        val font = generator.generateFont(param)
        generator.dispose()

        info = Label("", Label.LabelStyle(font, Color.LIGHT_GRAY))
        info?.setPosition(10.0f, Gdx.graphics.height.toFloat() - 10.0f)
        info?.setAlignment(Align.topLeft, Align.topLeft)
        stage?.addActor(info)
    }

    override fun onStep(commands: SortedMap<Int, Command>) {
        synchronized(this) {
            lastCommands = commands
            totalSteps += 1
        }
    }

    private fun updateInfo() {
        val sb = StringBuilder()
                .appendln("Strategy: ${strategyName}")
                .appendln("Refresh interval: ${sleepTimeMs}ms" + (if (paused) " (paused)" else ""))
                .appendln("Number of bots: ${state.bots.count()}")
                .appendln("Harmonics: ${state.harmonics}")
                .appendln("Steps: ${totalSteps}")
                .appendln("Energy: ${state.energy}")
                .appendln("")
                .appendln("Last step commands:")

        for (pair in lastCommands) {
            sb.appendln("      ${pair.key} ${state.getBot(pair.key)?.pos}: ${pair.value}")
        }

        sb
                .appendln("")
                .appendln("Controls:")
                .appendln("      rotate: arrows or WASD")
                .appendln("      zoom: -/= or Q/E")
                .appendln("      speed: Z/X")
                .appendln("      pause: C")
                .appendln("      step: SPACE")
        synchronized(this) {
            info?.setText(sb.toString())
        }
    }

    fun update(newState: State) {
        while (paused && !step) {
            updateInfo()
            Thread.sleep(10)
        }
        if (step) {
            step = !step
        }
        state = newState
        updateInfo()
        Thread.sleep(if (paused) 10 else sleepTimeMs)
    }

    override fun render() {
        chunkModel?.dispose()
        chunkModel = state.toVisModel()
        instance = ModelInstance(chunkModel)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        keyboardControls()

        modelBatch.begin(camera)
        instance?.let { modelBatch.render(it, environment) }
        modelBatch.render(floorInstance, environment)
        modelBatch.end()

        synchronized(this) {
            stage?.draw()
        }
    }

    private fun keyboardControls() {
        val speed = 2f
        val instance = instance
        if (instance != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.Z)) {
                sleepTimeMs -= 10
                sleepTimeMs = Math.max(sleepTimeMs, 10)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.X)) {
                sleepTimeMs += 10
                sleepTimeMs = Math.min(sleepTimeMs, 500)
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
                paused = !paused
                updateInfo()
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                step = true
            }

            if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)
                    || Gdx.input.isKeyPressed(Input.Keys.E)) {
                instance.transform.scale(1.1f, 1.1f, 1.1f)
                floorInstance.transform.scale(1.1f, 1.1f, 1.1f)
            }

            if (Gdx.input.isKeyPressed(Input.Keys.MINUS)
                    || Gdx.input.isKeyPressed(Input.Keys.Q)) {
                instance.transform.scale(0.9f, 0.9f, 0.9f)
                floorInstance.transform.scale(0.9f, 0.9f, 0.9f)
            }

            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)
                    || Gdx.input.isKeyPressed(Input.Keys.D)) {
                instance.transform.rotate(0f, 1f, 0f, speed)
                floorInstance.transform.rotate(0f, 1f, 0f, speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.A)) {
                instance.transform.rotate(0f, 1f, 0f, -speed)
                floorInstance.transform.rotate(0f, 1f, 0f, -speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.UP)
                    || Gdx.input.isKeyPressed(Input.Keys.W)) {
                instance.transform.rotate(1f, 0f, 0f, speed)
                floorInstance.transform.rotate(1f, 0f, 0f, speed)
            }
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)
                    || Gdx.input.isKeyPressed(Input.Keys.S)) {
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
