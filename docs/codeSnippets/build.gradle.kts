buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath(libs.kotlin.gradle.plugin)
	}
}


allprojects {
	repositories {
		mavenCentral()
		maven("https://maven-repo.tigase.org/repository/release/")
		maven("https://maven-repo.tigase.org/repository/snapshot/")
	}
}