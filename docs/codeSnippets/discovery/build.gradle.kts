val halcyon_version: String by project
val kotlin_version: String by project

plugins {
	application
	kotlin("jvm")
}

kotlin {
	jvmToolchain(11)
}

application {
	mainClass.set("discovery.ApplicationKt")
}

dependencies {
	implementation("tigase.halcyon:halcyon-core:$halcyon_version")
}