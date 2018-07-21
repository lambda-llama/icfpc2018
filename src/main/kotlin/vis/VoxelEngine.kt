package io.github.lambdallama.vis

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import vis.floorModel
import vis.toVisModel
import com.badlogic.gdx.utils.viewport.ScreenViewport
import io.github.lambdallama.CurrentState
import io.github.lambdallama.Snapshot


class VoxelEngine(
        private val strategyName: String,
        private val currentState: CurrentState
) : ApplicationAdapter() {
    private var chunkModel: Model? = null
    lateinit var floorModel: Model
    lateinit var modelBatch: ModelBatch
    lateinit var environment: Environment
    private var stage: Stage? = null
    private var info: Label? = null

    private var transform = Matrix4().idt()

    private lateinit var cntl: FirstPersonCameraController

    private lateinit var camera: PerspectiveCamera

    override fun create() {
        modelBatch = ModelBatch()

        camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
                .apply {
                    position.set(10f, 10f, 10f)
                    lookAt(0f, 0f, 0f)
                    near = 1f
                    far = 300f
                }
        cntl = FirstPersonCameraController(camera).apply { setVelocity(10f) }
        Gdx.input.inputProcessor = cntl
        cntl.update()
        stage = Stage(ScreenViewport())

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))

        val snapshot = currentState.snapshot
        chunkModel = snapshot.state.toVisModel()
        floorModel = snapshot.state.floorModel()

        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/ubuntu.ttf"))
        val param = FreeTypeFontGenerator.FreeTypeFontParameter()
        param.size = 16
        param.color = Color.LIGHT_GRAY
        val font = generator.generateFont(param)
        generator.dispose()

        info = Label("", Label.LabelStyle(font, Color.LIGHT_GRAY))
                .apply {
                    setPosition(10.0f, Gdx.graphics.height.toFloat() - 10.0f)
                    setAlignment(Align.topLeft, Align.topLeft)
                }
        stage?.addActor(info)
    }

    private fun updateInfo(snapshot: Snapshot, delayMs: Double, paused: Boolean) {
        val state = snapshot.state
        val sb = StringBuilder()
                .appendln("Strategy: ${strategyName}")
                .appendln("Refresh interval: ${delayMs}ms" + (if (paused) " (paused)" else ""))
                .appendln("Number of bots: ${state.bots.count()}")
                .appendln("Harmonics: ${state.harmonics}")
                .appendln("Steps: ${snapshot.totalSteps}")
                .appendln("Energy: ${state.energy}")
                .appendln("")
                .appendln("Last step commands:")

        for (pair in snapshot.lastCommands) {
            sb.appendln("      ${pair.key} ${state[pair.key]?.pos}: ${pair.value}")
        }

        sb
                .appendln("")
                .appendln("Controls:")
                .appendln("      rotate: mouse")
                .appendln("      move: WASD")
                .appendln("      speed: Z/X")
                .appendln("      pause: C")
                .appendln("      step: SPACE")
        info?.setText(sb.toString())
    }

    fun update(snapshot: Snapshot, delayMs: Double, paused: Boolean) {
        updateInfo(snapshot, delayMs, paused)
        chunkModel?.dispose()
        chunkModel = snapshot.state.toVisModel()
    }

    override fun render() {
        update(currentState.snapshot, currentState.delayMs, currentState.paused)

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        keyboardControls()

        modelBatch.begin(camera)
        modelBatch.render(ModelInstance(chunkModel, transform), environment)
        modelBatch.render(ModelInstance(floorModel, transform), environment)
        modelBatch.end()

        stage?.draw()
    }

    private fun keyboardControls() {
        cntl.update()
        if (Gdx.input.isKeyPressed(Input.Keys.Z)) {
            currentState.delayMs *= 0.95
        }
        if (Gdx.input.isKeyPressed(Input.Keys.X)) {
            currentState.delayMs *= 1.05
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            currentState.paused = !currentState.paused
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            currentState.step = true
        }
    }

    override fun dispose() {
        chunkModel?.dispose()
        floorModel.dispose()
    }
}
