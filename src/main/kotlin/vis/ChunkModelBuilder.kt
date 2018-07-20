package vis

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.brzez.voxelengine.chunk.ChunkBlockSide
import io.github.lambdallama.Matrix

class ChunkModelBuilder {
    protected val vertexBuffer: FloatArray
    protected var vertexBufferPosition = 0
    protected val indexBuffer: ShortArray
    protected var indexBufferPosition = 0

    protected var nVerts = 0

    protected val blockSize = .5f
    private var chunkData: Matrix? = null

    init {
        vertexBuffer = FloatArray(16 * 16 * 16 * 3 * 2 * 8 * 2) // no idea how big it should be. Don't care for now.
        indexBuffer = ShortArray(1337 * 8 * 8) // 8-)
    }

    protected fun addVertex(x: Float, y: Float, z: Float, normal: Vector3) {
        nVerts++
        vertexBuffer[vertexBufferPosition++] = x
        vertexBuffer[vertexBufferPosition++] = y
        vertexBuffer[vertexBufferPosition++] = z

        vertexBuffer[vertexBufferPosition++] = normal.x
        vertexBuffer[vertexBufferPosition++] = normal.y
        vertexBuffer[vertexBufferPosition++] = normal.z
    }

    protected fun addIndex(vararg indices: Int) {
        for (i in indices) {
            indexBuffer[indexBufferPosition++] = (nVerts + i).toShort()
        }
    }

    protected fun addMeshFromBuffers(meshBuilder: MeshPartBuilder) {
        val vertices = FloatArray(vertexBufferPosition)
        val indices = ShortArray(indexBufferPosition)

        System.arraycopy(vertexBuffer, 0, vertices, 0, vertexBufferPosition)
        System.arraycopy(indexBuffer, 0, indices, 0, indexBufferPosition)

        meshBuilder.addMesh(vertices, indices)

        nVerts = 0
        indexBufferPosition = nVerts
        vertexBufferPosition = indexBufferPosition
    }

    protected fun isEmpty(x: Int, y: Int, z: Int): Boolean {
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

    protected fun addBlock(x: Int, y: Int, z: Int) {
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

    protected fun getBlockSides(x: Int, y: Int, z: Int): Int {
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
        meshBuilder = builder.part("potato", GL20.GL_TRIANGLES, (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(), Material(ColorAttribute.createDiffuse(Color.FOREST)))

        for (x in 0 until chunkData.R) {
            for (y in 0 until chunkData.R) {
                for (z in 0 until chunkData.R) {
                    addBlock(x, y, z)
                }
            }
        }
        println("Verts: " + nVerts + " tris: " + nVerts / 6)
        addMeshFromBuffers(meshBuilder)
        return builder.end()
    }
}