plugins {
    application
    kotlin("jvm")
}

kotlin {
    jvmToolchain(jdkVersion = libs.versions.java.languageVersion.get().toInt())
}

application {
    mainClass.set("accountregistration.ApplicationKt")
}

dependencies {
    implementation(libs.halcyon.core)
}
