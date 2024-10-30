val halcyon_version: String by project
val kotlin_version: String by project

plugins {
	application
	kotlin("jvm")
}

kotlin {
	jvmToolchain(17)
}

application {
	mainClass.set("simpleclient.ApplicationKt")
}

dependencies {
	implementation("tigase.halcyon:halcyon-core:$halcyon_version")
}