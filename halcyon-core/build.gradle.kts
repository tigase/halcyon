/*
* halcyon-core
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
	kotlin("multiplatform")
	`maven-publish`
	signing
	kotlin("plugin.serialization")
	id("org.jetbrains.dokka")
}

kotlin {
	jvmToolchain(11)
	jvm {
		withJava()
		testRuns["test"].executionTask.configure {
			useJUnit()
		}
	}
	js(IR) {
		browser {
			commonWebpackConfig { }
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}
	ios() {
		// TODO: Before compilation you need to download https://github.com/tigase/openssl-swiftpm/releases/download/1.1.171/OpenSSL.xcframework.zip to "frameworks" directory and unpack this ZIP file.
		// TODO: Before compilation it is required to go to OpenSSL.xcframework to each subdirectory and Headers and move all files there to "openssl" subdirectory inside Headers
		val frameworkDir = if (System.getenv("SDK_NAME")
				?.startsWith("iphoneos") == true
		) {
			"$rootDir/frameworks/OpenSSL.xcframework/ios-arm64_armv7"
		} else {
			"$rootDir/frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator"
		}
		compilations.getByName("main") {
			cinterops {
				val OpenSSL by creating {
					defFile("src/nativeInterop/cinterop/OpenSSL.def")
					includeDirs("$frameworkDir/")
					compilerOpts(
						"-F$frameworkDir", "-framework", "OpenSSL"
					)
				}
			}
			kotlinOptions.freeCompilerArgs = listOf("-include-binary", "$frameworkDir/OpenSSL.framework/OpenSSL")
			binaries.all {
				linkerOpts(
					"-F$frameworkDir",
					"-framework",
					"OpenSSL",
//					"-rpath",
//					"@loader_path/Frameworks",
//					"-rpath",
//					"@executable_path/Frameworks",
//					"-rpath", frameworkDir
				)
			}
		}
		binaries {
			staticLib {}
		}
	}
	// Same target as above for iOS but for Arm64 simulator (simulator in AppleSilicon machine)
	iosSimulatorArm64() {
		val frameworkDir = "$rootDir/frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator"
		compilations.getByName("main") {
			cinterops {
				val OpenSSL by creating {
					defFile("src/nativeInterop/cinterop/OpenSSL.def")
					includeDirs("$frameworkDir/")
					compilerOpts(
						"-F$frameworkDir", "-framework", "OpenSSL"
					)
				}
			}
			kotlinOptions.freeCompilerArgs = listOf("-include-binary", "$frameworkDir/OpenSSL.framework/OpenSSL")
			binaries.all {
				linkerOpts(
					"-F$frameworkDir",
					"-framework",
					"OpenSSL",
//					"-rpath",
//					"@loader_path/Frameworks",
//					"-rpath",
//					"@executable_path/Frameworks",
//					"-rpath", frameworkDir
				)
			}
		}
	}

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.RequiresOptIn")
			}
		}
		val commonMain by getting {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation(deps.kotlinx.serialization.core)
				implementation(deps.kotlinx.datetime)
				implementation(deps.krypto)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test"))
				implementation(deps.kotlinx.serialization.json)
			}
		}
		val omemoMain by creating {
			dependsOn(commonMain)
		}
		val omemoTest by creating {
			dependsOn(commonTest)
		}
		val jvmMain by getting {
			dependsOn(omemoMain)
			dependencies {
				implementation(deps.minidns)
				implementation(deps.signal.protocol.java)
			}
		}
		val jvmTest by getting {
			dependsOn(omemoTest)
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
		val jsMain by getting  {
			dependsOn(commonMain)
			dependencies {
				implementation(kotlin("stdlib-js"))
			}
		}
		val jsTest by getting {
			dependsOn(commonTest)
			dependencies {
				implementation(kotlin("test-js"))
			}
		}
		val iosMain by getting {
			dependsOn(omemoMain)
			dependencies {
				implementation(deps.kotlinx.datetime)
			}
		}
		val iosSimulatorArm64Main by getting {
			dependsOn(iosMain)
		}
	}
}

//tasks["clean"].doLast {
//	delete("$rootDir/frameworks/OpenSSL.xcframework")
//}
//
tasks["cinteropOpenSSLIosArm64"].dependsOn("prepareOpenSSL")
tasks["cinteropOpenSSLIosX64"].dependsOn("prepareOpenSSL")

tasks.register("prepareOpenSSL") {
	description = "Downloads and unpack OpenSSL XCFramework."
	val zipUrl = "https://github.com/tigase/openssl-swiftpm/releases/download/1.1.171/OpenSSL.xcframework.zip"

	fun download(url: String, path: String) = ant.invokeMethod("get", mapOf("src" to url, "dest" to File(path)))

	doLast {
		if (!File("$rootDir/frameworks/OpenSSL.xcframework.zip").exists()) {
			logger.lifecycle("Downloading OpenSSL framework...")
			download(
				zipUrl, "$rootDir/frameworks/"
			)
		}
		if (!File("$rootDir/frameworks/OpenSSL.xcframework").exists()) {
			logger.lifecycle("Unzipping OpenSSL framework...")
			copy {
				from(zipTree("$rootDir/frameworks/OpenSSL.xcframework.zip"))
				into("$rootDir/frameworks/")
			}
		}
	}
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
		moduleName.set("Tigase Halcyon")
		moduleVersion.set(project.version.toString())
		failOnWarning.set(false)
		suppressObviousFunctions.set(true)
		suppressInheritedMembers.set(false)
		offlineMode.set(false)
	}
