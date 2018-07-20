package lambdallama.github.io

import com.google.common.io.Resources
import org.junit.Test

class ModelTest {
    @Test
    fun testParse() = withTempFile("test", ".mdl") { file ->
        file.outputStream().use { os ->
            Resources.copy(Resources.getResource("LA001_tgt.mdl"), os)
        }

        val (matrix) = Model.parse(file)
        for (x in 0 until matrix.R) {
            for (y in 0 until matrix.R) {
                for (z in 0 until matrix.R) {
                    matrix[x, y, z]  // Just make sure it doesn't crash.
                }
            }
        }
    }
}