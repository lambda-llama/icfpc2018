plugins {
    application
    kotlin("jvm") version "1.2.51"
}

application {
    mainClassName = "lambdallama.github.io.MainKt"
}

dependencies {
    compile(kotlin("stdlib"))
    testCompile("junit:junit:4.12")
}

repositories {
    jcenter()
}
