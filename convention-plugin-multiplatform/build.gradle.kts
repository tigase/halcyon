plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    runtimeOnly(libs.kotlin.gradle.plugin)
}
