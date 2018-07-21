package vis

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import io.github.lambdallama.Coord
import io.github.lambdallama.Harmonics
import io.github.lambdallama.Matrix

private const val blockSize = .5f
private const val halfSize = blockSize * 0.5f

fun io.github.lambdallama.State.toVisModel(): Model {
    val builder = ModelBuilder()
    builder.begin()
    val meshBuilder: MeshPartBuilder
    meshBuilder = builder.part(
            "potato",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            Material(ColorAttribute.createDiffuse(Color.FOREST))
    )

    val fb = FaceBuffer()
    matrix.forEach { x, y, z -> addBlock(fb, matrix, matrix.blockSides(x, y, z), x, y, z) }
    fb.addMeshFromBuffers(meshBuilder)

    builder.node()

    val botMeshBuilder: MeshPartBuilder
    botMeshBuilder = builder.part(
            "bot",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            Material(ColorAttribute.createDiffuse(
                    if (harmonics == Harmonics.High) Color.FIREBRICK else Color.SKY))
    )

    val botFb = FaceBuffer()
    bots.mapNotNull { it?.pos }.forEach { pos ->
        addBlock(botFb, matrix, ChunkBlockSide.ALL, pos.x, pos.y, pos.z)
    }
    botFb.addMeshFromBuffers(botMeshBuilder)

    val highlight: List<Coord> = listOf(Coord(52,23,35), Coord(50,22,34))
    val hiFb = FaceBuffer()
    highlight.forEach { pos ->
        addBlock(hiFb, matrix, ChunkBlockSide.ALL, pos.x, pos.y, pos.z)
    }
    val mb= builder.part(
            "hightlihgh",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            Material(ColorAttribute.createDiffuse(Color.RED))
    )
    hiFb.addMeshFromBuffers(mb)


    return builder.end()
}

fun io.github.lambdallama.State.floorModel(): Model {
    val builder = ModelBuilder()
    builder.begin()
    val meshBuilder = builder.part(
            "floor",
            GL20.GL_TRIANGLES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            Material(ColorAttribute.createDiffuse(Color.CORAL), IntAttribute.createCullFace(GL20.GL_NONE))
    )

    val floorSize = blockSize * matrix.R.toFloat()
    val normal = Vector3(0.0f, 1.0f, 0.0f)
    val position = Vector3()
    position.sub(matrix.R.toFloat() * blockSize * .5f)

    val fb = FaceBuffer()
    fb.addSquare(
            position.x + floorSize, position.y - halfSize, position.z - floorSize,
            position.x - floorSize, position.y - halfSize, position.z - floorSize,
            position.x - floorSize, position.y - halfSize, position.z + floorSize,
            position.x + floorSize, position.y - halfSize, position.z + floorSize,
            normal
    )
    fb.addMeshFromBuffers(meshBuilder)
    return builder.end()
}


private class FaceBuffer {
    private val vertexBuffer: FloatArray = FloatArray(16 * 16 * 16 * 3 * 2 * 8 * 2 * 10)
    private val indexBuffer: ShortArray = ShortArray(1337 * 8 * 8 * 10)
    private var vertexBufferPosition = 0
    private var indexBufferPosition = 0
    private var nVerts = 0

    fun addSquare(
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            x4: Float, y4: Float, z4: Float,
            normal: Vector3
    ) {
        for (i in listOf(0, 1, 2, 2, 3, 0)) {
            indexBuffer[indexBufferPosition++] = (nVerts + i).toShort()
        }

        addVertex(x1, y1, z1, normal)
        addVertex(x2, y2, z2, normal)
        addVertex(x3, y3, z3, normal)
        addVertex(x4, y4, z4, normal)
    }

    private fun addVertex(x: Float, y: Float, z: Float, normal: Vector3) {
        nVerts++
        vertexBuffer[vertexBufferPosition++] = x
        vertexBuffer[vertexBufferPosition++] = y
        vertexBuffer[vertexBufferPosition++] = z

        vertexBuffer[vertexBufferPosition++] = normal.x
        vertexBuffer[vertexBufferPosition++] = normal.y
        vertexBuffer[vertexBufferPosition++] = normal.z
    }

    fun addMeshFromBuffers(meshBuilder: MeshPartBuilder) {
        val vertices = FloatArray(vertexBufferPosition)
        val indices = ShortArray(indexBufferPosition)

        System.arraycopy(vertexBuffer, 0, vertices, 0, vertexBufferPosition)
        System.arraycopy(indexBuffer, 0, indices, 0, indexBufferPosition)

        meshBuilder.addMesh(vertices, indices)
    }
}

private fun addBlock(faceBuilder: FaceBuffer, matrix: Matrix, sides: Int, x: Int, y: Int, z: Int) {
    val halfSize = blockSize * .5f

    val position = Vector3(x.toFloat(), y.toFloat(), z.toFloat()).scl(blockSize)

    // offset so (0,0,0) is the center
    position.sub(matrix.R.toFloat() * blockSize * .5f)

    val normal = Vector3()

    if (sides and ChunkBlockSide.FRONT != 0) {
        normal.set(0f, 0f, 1f)
        faceBuilder.addSquare(
                position.x + -halfSize, position.y + halfSize, position.z + halfSize,
                position.x + -halfSize, position.y + -halfSize, position.z + halfSize,
                position.x + halfSize, position.y + -halfSize, position.z + halfSize,
                position.x + halfSize, position.y + halfSize, position.z + halfSize,
                normal
        )
    }
    if (sides and ChunkBlockSide.BACK != 0) {
        normal.set(0f, 0f, -1f)
        faceBuilder.addSquare(
                position.x + halfSize, position.y + -halfSize, position.z - halfSize,
                position.x + -halfSize, position.y + -halfSize, position.z - halfSize,
                position.x + -halfSize, position.y + halfSize, position.z - halfSize,
                position.x + halfSize, position.y + halfSize, position.z - halfSize,
                normal
        )
    }
    if (sides and ChunkBlockSide.TOP != 0) {
        normal.set(0f, -1f, 0f)
        faceBuilder.addSquare(
                position.x - halfSize, position.y - halfSize, position.z + halfSize,
                position.x - halfSize, position.y - halfSize, position.z - halfSize,
                position.x + halfSize, position.y - halfSize, position.z - halfSize,
                position.x + halfSize, position.y - halfSize, position.z + halfSize,
                normal
        )
    }
    if (sides and ChunkBlockSide.BOTTOM != 0) {
        normal.set(0f, 1f, 0f)
        faceBuilder.addSquare(
                position.x + halfSize, position.y + halfSize, position.z - halfSize,
                position.x - halfSize, position.y + halfSize, position.z - halfSize,
                position.x - halfSize, position.y + halfSize, position.z + halfSize,
                position.x + halfSize, position.y + halfSize, position.z + halfSize,
                normal
        )
    }
    if (sides and ChunkBlockSide.LEFT != 0) {
        normal.set(-1f, 0f, 0f)
        faceBuilder.addSquare(
                position.x - halfSize, position.y + halfSize, position.z - halfSize,
                position.x - halfSize, position.y - halfSize, position.z - halfSize,
                position.x - halfSize, position.y - halfSize, position.z + halfSize,
                position.x - halfSize, position.y + halfSize, position.z + halfSize,
                normal
        )
    }
    if (sides and ChunkBlockSide.RIGHT != 0) {
        normal.set(1f, 0f, 0f)
        faceBuilder.addSquare(
                position.x + halfSize, position.y - halfSize, position.z + halfSize,
                position.x + halfSize, position.y - halfSize, position.z - halfSize,
                position.x + halfSize, position.y + halfSize, position.z - halfSize,
                position.x + halfSize, position.y + halfSize, position.z + halfSize,
                normal
        )
    }
}

private fun Matrix.blockSides(x: Int, y: Int, z: Int): Int {
    if (!this[x, y, z]) return 0
    var sides = 0

    if (isEmpty(x, y, z + 1)) sides = sides or ChunkBlockSide.FRONT
    if (isEmpty(x, y, z - 1)) sides = sides or ChunkBlockSide.BACK

    if (isEmpty(x, y - 1, z)) sides = sides or ChunkBlockSide.TOP
    if (isEmpty(x, y + 1, z)) sides = sides or ChunkBlockSide.BOTTOM

    if (isEmpty(x - 1, y, z)) sides = sides or ChunkBlockSide.LEFT
    if (isEmpty(x + 1, y, z)) sides = sides or ChunkBlockSide.RIGHT

    return sides
}

private fun Matrix.isEmpty(x: Int, y: Int, z: Int): Boolean {
    if (x < 0 || x > R) return false
    if (y < 0 || y > R) return false
    if (z < 0 || z > R) return false
    return !this[x, y, z]
}
