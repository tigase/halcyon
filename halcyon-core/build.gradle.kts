/*
 * Tigase Halcyon XMPP Library
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
plugins {
	kotlin("multiplatform") version "1.3.72"
	kotlin("plugin.serialization") version "1.3.72"
	id("maven-publish")
}

group = "tigase.halcyon"
version = "0.0.1"


repositories {
	mavenCentral()
}

kotlin {
	jvm() // Creates a JVM target with the default name 'jvm'
	js {
		browser {
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}

	sourceSets {
		val commonMain by getting {
			languageSettings.apply {
				languageVersion = "1.3" // possible values: '1.0', '1.1', '1.2', '1.3'
				apiVersion = "1.3" // possible values: '1.0', '1.1', '1.2', '1.3'
			}
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:0.20.0")
				implementation("com.soywiz.korlibs.krypto:krypto:1.10.1")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
		jvm().compilations["main"].defaultSourceSet {
			dependencies {
				implementation(kotlin("stdlib-jdk8"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
				implementation("com.soywiz.korlibs.krypto:krypto-jvm:1.10.1")
				implementation("org.minidns:minidns-hla:0.3.1")
			}
		}
		jvm().compilations["test"].defaultSourceSet {
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
		js().compilations["main"].defaultSourceSet {
			dependencies {
				implementation(kotlin("stdlib-js"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:0.20.0")
				implementation("com.soywiz.korlibs.krypto:krypto-js:1.10.1")
			}
		}
		js().compilations["test"].defaultSourceSet {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}

	}
}

//kotlin {
//	jvm()
//	js()
//	// For ARM, should be changed to iosArm32 or iosArm64
//	// For Linux, should be changed to e.g. linuxX64
//	// For MacOS, should be changed to e.g. macosX64
//	// For Windows, should be changed to e.g. mingwX64
////	macosX64("macos")
//
//	targets {
//		jvm()
//		fromPreset(presets.js, "js") {
//			compileKotlinJs {
//				kotlinOptions.metaInfo = true
//				kotlinOptions.outputFile = "$project.buildDir.path/js/${project.name}.js"
//				kotlinOptions.sourceMap = true
//				kotlinOptions.moduleKind = "commonjs"
//				kotlinOptions.main = "call"
//			}
//		}
//	}
//
//	sourceSets {
//		commonMain {
//			dependencies {
//				implementation kotlin('stdlib-common')
//				implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serialization_version"
//				implementation "com.soywiz.korlibs.krypto:krypto:1.10.1"
//			}
//		}
//		commonTest {
//			dependencies {
//				implementation kotlin('test-common')
//				implementation kotlin('test-annotations-common')
//			}
//		}
//		jvmMain {
//			dependencies {
//				implementation kotlin('stdlib-jdk8')
//				implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serialization_version"
//				implementation "com.soywiz.korlibs.krypto:krypto-jvm:1.10.1"
//
//				implementation 'org.minidns:minidns-hla:0.3.1'
//			}
//		}
//		jvmTest {
//			dependencies {
//				implementation kotlin('test')
//				implementation kotlin('test-junit')
//			}
//		}
//		jsMain {
//			dependencies {
//				implementation kotlin('stdlib-js')
//				implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serialization_version"
//				implementation "com.soywiz.korlibs.krypto:krypto-js:1.10.1"
//
//			}
//		}
//		jsTest {
//			dependencies {
//				implementation kotlin('test-js')
//			}
//		}
////		macosMain {
////		}
////		macosTest {
////		}
//	}
//}