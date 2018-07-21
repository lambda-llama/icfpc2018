import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    kotlin("jvm") version "1.2.51"
}

repositories {
    jcenter()
    mavenCentral()
    maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") }
    maven { setUrl("https://oss.sonatype.org/content/repositories/releases/") }

}

val gdxVersion = "1.9.4"
dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("khttp:khttp:0.1.0")
    compile("com.google.guava:guava:25.1-jre")
    compile("net.sf.trove4j:trove4j:3.0.3")
    compile("com.badlogicgames.gdx:gdx:$gdxVersion")
    compile("com.badlogicgames.gdx:gdx-backend-lwjgl:$gdxVersion")
    compile("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    compile("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    compile("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")

    testCompile("junit:junit:4.12")
}

application {
    mainClassName = "io.github.lambdallama.MainKt"
}
kotlin {
    experimental.coroutines = Coroutines.ENABLE
}
