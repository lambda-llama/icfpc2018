package lambdallama.github.io

class State {
    fun loadFrom(/* */) { }

    /* Commands */
    fun halt(bot: Int) { }

    fun wait(bot: Int) { }

    fun flip(bot: Int) { }

    fun sMove(bot: Int, dx: Int, dy: Int, dz: Int) { }

    fun lMove(bot: Int, dx0: Int, dy0: Int, dz0: Int, dx1: Int, dy1: Int, dz1: Int) { }

    fun fission(bot: Int, dx: Int, dy: Int, dz: Int, m: Int) { }

    fun fill(bot: Int, dx: Int, dy: Int, dz: Int) { }

    fun fusion(pBot: Int, sBot: Int) { }
};
