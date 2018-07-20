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
import io.github.lambdallama.Matrix

fun io.github.lambdallama.Model.toVisModel(): Model =
        ChunkModelBuilder().build(this.matrix)

fun io.github.lambdallama.Model.floorModel(): Model = ChunkModelBuilder().buildFloor(this.matrix)

private class ChunkModelBuilder {
    private val vertexBuffer: FloatArray = FloatArray(16 * 16 * 16 * 3 * 2 * 8 * 2 * 10)
    private var vertexBufferPosition = 0
    private val indexBuffer: ShortArray = ShortArray(1337 * 8 * 8 * 10)
    private var indexBufferPosition = 0

    private var nVerts = 0

    private val blockSize = .5f
    private var chunkData: Matrix? = null

    fun addVertex(x: Float, y: Float, z: Float, normal: Vector3) {
        nVerts++
        vertexBuffer[vertexBufferPosition++] = x
        vertexBuffer[vertexBufferPosition++] = y
        vertexBuffer[vertexBufferPosition++] = z

        vertexBuffer[vertexBufferPosition++] = normal.x
        vertexBuffer[vertexBufferPosition++] = normal.y
        vertexBuffer[vertexBufferPosition++] = normal.z
    }

    fun addIndex(vararg indices: Int) {
        for (i in indices) {
            indexBuffer[indexBufferPosition++] = (nVerts + i).toShort()
        }
    }

    fun addMeshFromBuffers(meshBuilder: MeshPartBuilder) {
        val vertices = FloatArray(vertexBufferPosition)
        val indices = ShortArray(indexBufferPosition)

        System.arraycopy(vertexBuffer, 0, vertices, 0, vertexBufferPosition)
        System.arraycopy(indexBuffer, 0, indices, 0, indexBufferPosition)

        meshBuilder.addMesh(vertices, indices)

        nVerts = 0
        indexBufferPosition = nVerts
        vertexBufferPosition = indexBufferPosition
    }

    private fun isEmpty(x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || x > chunkData!!.R) {
            return false
        }
        if (y < 0 || y > chunkData!!.R) {
            return false
        }
        return if (z < 0 || z > chunkData!!.R) {
            false
        } else !chunkData!![x, y, z]

    }

    private fun addBlock(x: Int, y: Int, z: Int) {
        val sides = getBlockSides(x, y, z)
        val halfSize = blockSize * .5f

        val position = Vector3(x.toFloat(), y.toFloat(), z.toFloat()).scl(blockSize)

        // offset so (0,0,0) is the center
        position.sub(chunkData!!.R.toFloat() * blockSize * .5f)

        val normal = Vector3()

        if (sides and ChunkBlockSide.FRONT != 0) {
            normal.set(0f, 0f, 1f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x + -halfSize, position.y + halfSize, position.z + halfSize, normal)
            addVertex(position.x + -halfSize, position.y + -halfSize, position.z + halfSize, normal)
            addVertex(position.x + halfSize, position.y + -halfSize, position.z + halfSize, normal)
            addVertex(position.x + halfSize, position.y + halfSize, position.z + halfSize, normal)
        }
        if (sides and ChunkBlockSide.BACK != 0) {
            normal.set(0f, 0f, -1f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x + halfSize, position.y + -halfSize, position.z - halfSize, normal)
            addVertex(position.x + -halfSize, position.y + -halfSize, position.z - halfSize, normal)
            addVertex(position.x + -halfSize, position.y + halfSize, position.z - halfSize, normal)
            addVertex(position.x + halfSize, position.y + halfSize, position.z - halfSize, normal)
        }
        if (sides and ChunkBlockSide.TOP != 0) {
            normal.set(0f, -1f, 0f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x - halfSize, position.y - halfSize, position.z + halfSize, normal)
            addVertex(position.x - halfSize, position.y - halfSize, position.z - halfSize, normal)
            addVertex(position.x + halfSize, position.y - halfSize, position.z - halfSize, normal)
            addVertex(position.x + halfSize, position.y - halfSize, position.z + halfSize, normal)
        }
        if (sides and ChunkBlockSide.BOTTOM != 0) {
            normal.set(0f, 1f, 0f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x + halfSize, position.y + halfSize, position.z - halfSize, normal)
            addVertex(position.x - halfSize, position.y + halfSize, position.z - halfSize, normal)
            addVertex(position.x - halfSize, position.y + halfSize, position.z + halfSize, normal)
            addVertex(position.x + halfSize, position.y + halfSize, position.z + halfSize, normal)
        }
        if (sides and ChunkBlockSide.LEFT != 0) {
            normal.set(-1f, 0f, 0f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x - halfSize, position.y + halfSize, position.z - halfSize, normal)
            addVertex(position.x - halfSize, position.y - halfSize, position.z - halfSize, normal)
            addVertex(position.x - halfSize, position.y - halfSize, position.z + halfSize, normal)
            addVertex(position.x - halfSize, position.y + halfSize, position.z + halfSize, normal)
        }
        if (sides and ChunkBlockSide.RIGHT != 0) {
            normal.set(1f, 0f, 0f)
            addIndex(0, 1, 2, 2, 3, 0)
            addVertex(position.x + halfSize, position.y - halfSize, position.z + halfSize, normal)
            addVertex(position.x + halfSize, position.y - halfSize, position.z - halfSize, normal)
            addVertex(position.x + halfSize, position.y + halfSize, position.z - halfSize, normal)
            addVertex(position.x + halfSize, position.y + halfSize, position.z + halfSize, normal)
        }
    }

    private fun getBlockSides(x: Int, y: Int, z: Int): Int {
        if (!chunkData!![x, y, z]) {
            return 0
        }
        var sides = 0

        if (isEmpty(x, y, z + 1)) {
            sides = sides or ChunkBlockSide.FRONT
        }
        if (isEmpty(x, y, z - 1)) {
            sides = sides or ChunkBlockSide.BACK
        }

        if (isEmpty(x, y - 1, z)) {
            sides = sides or ChunkBlockSide.TOP
        }
        if (isEmpty(x, y + 1, z)) {
            sides = sides or ChunkBlockSide.BOTTOM
        }
        if (isEmpty(x - 1, y, z)) {
            sides = sides or ChunkBlockSide.LEFT
        }
        if (isEmpty(x + 1, y, z)) {
            sides = sides or ChunkBlockSide.RIGHT
        }

        return sides
    }

    fun build(chunkData: Matrix): Model {
        this.chunkData = chunkData
        val builder = ModelBuilder()
        builder.begin()
        val meshBuilder: MeshPartBuilder
        meshBuilder = builder.part(
                "potato",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                Material(ColorAttribute.createDiffuse(Color.FOREST))
        )

        for (x in 0 until chunkData.R) {
            for (y in 0 until chunkData.R) {
                for (z in 0 until chunkData.R) {
                    addBlock(x, y, z)
                }
            }
        }
        println("Verts: $nVerts tris: ${nVerts / 6}")
        addMeshFromBuffers(meshBuilder)
        return builder.end()
    }

    fun buildFloor(chunkData: Matrix): Model {
        val builder = ModelBuilder()
        builder.begin()
        val meshBuilder: MeshPartBuilder
        meshBuilder = builder.part(
                "floor",
                GL20.GL_TRIANGLES,
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
                Material(ColorAttribute.createDiffuse(Color.CORAL), IntAttribute.createCullFace(GL20.GL_NONE))
        )

        val halfSize = blockSize * 0.5f
        val floorSize = blockSize * chunkData.R.toFloat()
        val normal = Vector3(0.0f, 1.0f, 0.0f)
        val position = Vector3()
        position.sub(chunkData.R.toFloat() * blockSize * .5f)

        addIndex(0, 1, 2, 2, 3, 0)
        addVertex(position.x + floorSize, position.y - halfSize, position.z - floorSize, normal)
        addVertex(position.x - floorSize, position.y - halfSize, position.z - floorSize, normal)
        addVertex(position.x - floorSize, position.y - halfSize, position.z + floorSize, normal)
        addVertex(position.x + floorSize, position.y - halfSize, position.z + floorSize, normal)

        addMeshFromBuffers(meshBuilder)
        return builder.end()
    }
}
