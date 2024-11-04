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
	id("kotlinMultiplatformConvention")
	`maven-publish`
	signing
	alias(libs.plugins.kotlinx.serialization)
	alias(libs.plugins.jetbrains.dokka)
}

kotlin {

	iosArm64 {
		// TODO: Before compilation you need to download https://github.com/tigase/openssl-swiftpm/releases/download/1.1.171/OpenSSL.xcframework.zip to "frameworks" directory and unpack this ZIP file.
		// TODO: Before compilation it is required to go to OpenSSL.xcframework to each subdirectory and Headers and move all files there to "openssl" subdirectory inside Headers
		val openSSLFrameworkDir = "$rootDir/frameworks/OpenSSL.xcframework/ios-arm64_armv7";
		val libsignalFrameworkDir = "$rootDir/frameworks/libsignal.xcframework/ios-arm64"
		compilations.getByName("main") {
			cinterops {
				val OpenSSL by creating {
					defFile("src/nativeInterop/cinterop/OpenSSL.def")
					includeDirs("$openSSLFrameworkDir/")
					compilerOpts(
						"-F$openSSLFrameworkDir", "-framework", "OpenSSL"
					)
				}
				val libsignal by creating {
					defFile("src/nativeInterop/cinterop/libsignal.def")
					includeDirs("$libsignalFrameworkDir/")
					compilerOpts(
						"-F$libsignalFrameworkDir", "-framework", "libsignal"
					)
				}
			}
			kotlinOptions.freeCompilerArgs = listOf(
				"-include-binary",
				"$openSSLFrameworkDir/OpenSSL.framework/OpenSSL",
				"-include-binary",
				"$libsignalFrameworkDir/libsignal.framework/libsignal"
			)
			binaries.all {
				linkerOpts(
					"-F$openSSLFrameworkDir",
					"-framework",
					"OpenSSL",
					"-F$libsignalFrameworkDir",
					"-framework",
					"libsignal"
				)
			}
		}
	}
	// Same target as above for iOS but for Arm64 simulator (simulator in AppleSilicon machine)
	iosSimulatorArm64 {
		val openSSLFrameworkDir = "$rootDir/frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator"
		val libsignalFrameworkDir = "$rootDir/frameworks/libsignal.xcframework/ios-arm64_x86_64-simulator"
		compilations.getByName("main") {
			cinterops {
				val OpenSSL by creating {
					defFile("src/nativeInterop/cinterop/OpenSSL.def")
					includeDirs("$openSSLFrameworkDir/")
					compilerOpts(
						"-F$openSSLFrameworkDir", "-framework", "OpenSSL"
					)
				}
				val libsignal by creating {
					defFile("src/nativeInterop/cinterop/libsignal.def")
					includeDirs("$libsignalFrameworkDir/")
					compilerOpts(
						"-F$libsignalFrameworkDir", "-framework", "libsignal"
					)
				}
			}
			kotlinOptions.freeCompilerArgs = listOf(
				"-include-binary",
				"$openSSLFrameworkDir/OpenSSL.framework/OpenSSL",
				"-include-binary",
				"$libsignalFrameworkDir/libsignal.framework/libsignal"
			)
			binaries.all {
				linkerOpts(
					"-F$openSSLFrameworkDir",
					"-framework",
					"OpenSSL",
					"-F$libsignalFrameworkDir",
					"-framework",
					"libsignal"
				)
			}
		}
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(libs.kotlinx.serialization.core)
				implementation(libs.kotlinx.datetime)
				implementation(libs.krypto)
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(libs.kotlinx.serialization.json)
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
				implementation(libs.minidns)
				implementation(libs.signal.protocol.java)
			}
		}
		val jvmTest by getting {
			dependsOn(omemoTest)
		}
		val jsMain by getting  {
			dependsOn(commonMain)
		}
		val jsTest by getting {
			dependsOn(commonTest)
		}
		val iosMain by getting {
			dependsOn(omemoMain)
			dependencies {
				implementation(libs.kotlinx.datetime)
			}
		}
		val iosArm64Main by getting {
			dependsOn(iosMain)
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

tasks["cinteropLibsignalIosArm64"].dependsOn("prepareLibsignal")
tasks["cinteropLibsignalIosSimulatorArm64"].dependsOn("prepareLibsignal")
tasks.register("prepareLibsignal") {
	description = "Download and unpack libsignal XCFramework."
	val zipUrl = "https://github.com/tigase/libsignal/releases/download/1.0.0/libsignal.xcframework.zip"

	fun download(url: String, path: String) = ant.invokeMethod("get", mapOf("src" to url, "dest" to File(path)))

	doLast {
		if (!File("$rootDir/frameworks/libsignal.xcframework.zip").exists()) {
			logger.lifecycle("Downloading libsignal framework...")
			download(
				zipUrl, "$rootDir/frameworks/"
			)
		}
		if (!File("$rootDir/frameworks/libsignal.xcframework").exists()) {
			logger.lifecycle("Unzipping libsignal framework...")
			copy {
				from(zipTree("$rootDir/frameworks/libsignal.xcframework.zip"))
				into("$rootDir/frameworks/")
			}
		}
	}
}

tasks["cinteropOpenSSLIosArm64"].dependsOn("prepareOpenSSL")
tasks["cinteropOpenSSLIosSimulatorArm64"].dependsOn("prepareOpenSSL")

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
