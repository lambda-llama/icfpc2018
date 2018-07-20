import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.brzez.voxelengine.VoxelEngine
import khttp.get
import khttp.structures.authorization.BasicAuthorization

fun main(args: Array<String>) {
    val config = LwjglApplicationConfiguration().apply { forceExit = false }
    LwjglApplication(VoxelEngine(), config)
}
