plugins {
    application
    kotlin("jvm") version "1.2.51"
}

application {
    mainClassName = "MainKt"
}
val gdxVersion = "1.9.4"
dependencies {
    compile(kotlin("stdlib-jre8"))
    compile("khttp:khttp:0.1.0")
    compile("com.google.guava:guava:25.1-jre")
    compile(project(":jve"))
    compile("com.badlogicgames.gdx:gdx-backend-lwjgl:$gdxVersion")
    compile("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    testCompile("junit:junit:4.12")
}

repositories {
    jcenter()
}
