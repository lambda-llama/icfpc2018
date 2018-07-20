package io.github.lambdallama

import kotlin.math.min

fun State.multiSMove(b: BotView, target: Coord) {
    while (b.pos.x < target.x - 1) {
        sMove(b.id, DeltaCoord(min(5, target.x - 1 - b.pos.x), 0, 0))
    }
    while (b.pos.y < target.y - 1) {
        sMove(b.id, DeltaCoord(0, min(5, target.y - 1 - b.pos.y), 0))
    }
    while (b.pos.z < target.z - 1) {
        sMove(b.id, DeltaCoord(0, 0, min(5, target.z - 1 - b.pos.z)))
    }
}

fun baseline(model: Model): State {
    val state = State.forModel(model)
    val (minCoord, maxCoord) = model.bbox
    val b = state.getBot(1)

    state.flip(b.id)
    state.multiSMove(b, minCoord + DeltaCoord(-1, -1, -1))

    for (y in 0 until model.matrix.R) {
        model.matrix.forEach(minCoord.copy(y = y), maxCoord.copy(y = y)) { x, _, z ->
            val coord = Coord(x, y, z)
            if (b.pos == coord) {
                return@forEach
            }

            val delta = coord - b.pos  // mlen == 1.
            if (model.matrix[coord]) {
                state.fill(b.id, delta)
            }
            state.sMove(b.id, delta)
        }
    }
    check(state.matrix == model.matrix)

    state.flip(b.id)
    state.multiSMove(b, Coord.ZERO)
    return state
}