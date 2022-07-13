plugins {
	`java-gradle-plugin`
	`maven-publish`
	kotlin("jvm")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("com.google.devtools.ksp:symbol-processing-api:1.6.10-1.0.2")
}

gradlePlugin {
	plugins {
		create("buildconfig") {
			id = "tigase.halcyon.serializer"
			displayName = "Gradle Serializer Plugin"
			implementationClass = "tigase.halcyon.serializer.gradle.plugins.SerializerPlugin"
		}
	}
}

kotlin {
//	jvm()
	sourceSets {
		val main by getting {
			dependencies {
				implementation(project(":serializer:annotations"))
				implementation("com.squareup:javapoet:1.12.1")
				implementation("com.google.devtools.ksp:symbol-processing-api:1.6.21-1.0.6")
			}
			kotlin.srcDir("src/main/kotlin")
			resources.srcDir("src/main/resources")
		}
	}
}