package io.github.lambdallama

import com.google.common.io.Resources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixTest {
    @Test
    fun testSetGet() {
        val m = Matrix(2, ByteArray(1))
        for (x in 0 until m.R) {
            for (y in 0 until m.R) {
                for (z in 0 until m.R) {
                    val message = "x = $x, y = $y, z = $z"
                    assertFalse(message, m[x, y, z])
                    m[x, y, z] = true
                    assertTrue(message, m[x, y, z])
                }
            }
        }
    }

    @Test
    fun testWellFormedEmpty() {
        val m = Matrix(2, ByteArray(1))
        assertTrue(m.isWellFormed)
    }

    @Test
    fun testWellFormedSingle() {
        val m = Matrix(3, ByteArray(4))
        m[1, 0, 1] = true
        assertTrue(m.isWellFormed)
    }

    @Test
    fun testWellFormedDoubleLinked() {
        val m = Matrix(3, ByteArray(4))
        m[1, 0, 1] = true
        m[1, 1, 1] = true
        assertTrue(m.isWellFormed)
    }
}

class ModelTest {
    @Test
    fun testParse() = withTempFile("test", ".mdl") { file ->
        file.outputStream().use { os ->
            Resources.copy(Resources.getResource("LA001_tgt.mdl"), os)
        }

        val (matrix) = Model.parse(file)
        assertTrue(matrix.isWellFormed)
    }
}
