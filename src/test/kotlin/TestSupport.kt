package io.github.lambdallama

import java.io.File

internal inline fun withTempFile(prefix: String, suffix: String,
                                 block: (File) -> Unit) {
    val file = File.createTempFile(prefix, suffix)
    try {
        block(file)
    } finally {
        check(file.delete()) { "failed to delete $file" }
    }
}
