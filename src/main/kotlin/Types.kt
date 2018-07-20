import com.google.common.math.IntMath
import java.io.File
import java.math.RoundingMode
import java.nio.ByteBuffer
import kotlin.experimental.and

data class Matrix(val R: Int, val coordinates: ByteArray) {
    /** Returns true if the voxel at a given coordinate is Full. */
    operator fun get(x: Int, y: Int, z: Int): Boolean {
        val offset = x * R * R + y * R + z
        val mask = 1 shl (offset % 8)
        return (coordinates[offset / 8] and mask.toByte()) == 0.toByte()
    }
}

data class Model(val matrix: Matrix) {
    companion object {
        fun parse(path: File): Model {
            val buf = ByteBuffer.wrap(path.readBytes())
            val R = buf.get().toInt()
            val shape = IntMath.divide(R * R * R, 8, RoundingMode.CEILING)
            val coordinates = ByteArray(shape).apply { buf.get(this) }
            if (path.extension == "xmdl") {
                TODO()
            }

            return Model(Matrix(R, coordinates))
        }
    }
}