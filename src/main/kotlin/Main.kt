package io.github.lambdallama

import khttp.get
import khttp.structures.authorization.BasicAuthorization

fun main(args: Array<String>) {
    val r = get("https://api.github.com/user", auth = BasicAuthorization("user", "pass"))
    r.statusCode
    println(r.statusCode)
// 200
    r.headers["Content-Type"]
// "application/json; charset=utf-8"
    r.text
// """{"type": "User"..."""
    r.jsonObject
    println(r.jsonObject)
    println(r.jsonObject["documentation_url"])
}
