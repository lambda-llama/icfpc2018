package vis

object ChunkBlockSide {
    const val TOP = 2
    const val BOTTOM = 8
    const val LEFT = 16
    const val RIGHT = 32
    const val FRONT = 64
    const val BACK = 128
    const val ALL = TOP or BOTTOM or LEFT or RIGHT or FRONT or BACK
}
