buildscript {
	val kotlin_version: String by project
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
	}
}


allprojects {
	repositories {
		mavenCentral()
		maven("https://maven-repo.tigase.org/repository/release/")
		maven("https://maven-repo.tigase.org/repository/snapshot/")
	}
}