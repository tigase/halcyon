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
		val openSSLFrameworkDir = "$rootDir/build/frameworks/OpenSSL.xcframework/ios-arm64_armv7";
		val libsignalFrameworkDir = "$rootDir/build/frameworks/libsignal.xcframework/ios-arm64"
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
			this@iosArm64.binaries.all {
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
		val openSSLFrameworkDir = "$rootDir/build/frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator"
		val libsignalFrameworkDir = "$rootDir/build/frameworks/libsignal.xcframework/ios-arm64_x86_64-simulator"
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
			this@iosSimulatorArm64.binaries.all {
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

	applyDefaultHierarchyTemplate()

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

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
		moduleName.set("Tigase Halcyon")
		moduleVersion.set(project.version.toString())
		failOnWarning.set(false)
		suppressObviousFunctions.set(true)
		suppressInheritedMembers.set(false)
		offlineMode.set(false)
	}

if (booleanProperty(Modules.ios.propertyName) || booleanProperty(Modules.iosSimulator.propertyName)) {
	val deleteOpenSSL = tasks.register<DeleteFrameworkTask>("deleteOpenSSL") {
		description = "Deleting OpenSSL XCFramework."
		frameworkName = "OpenSSL"
		frameworkZipPath = getFrameworkZipPath(frameworkName.get())
		frameworkDirectoryPath = getFrameworkDirectoryPath(frameworkName.get())
		outputs.cacheIf { false }
	}

	val deleteLibsignal = tasks.register<DeleteFrameworkTask>("deleteLibsignal") {
		description = "Deleting libsignal XCFramework."
		frameworkName = "libsignal"
		frameworkZipPath = getFrameworkZipPath(frameworkName.get())
		frameworkDirectoryPath = getFrameworkDirectoryPath(frameworkName.get())
		outputs.cacheIf { false }
	}

	tasks.clean {
		dependsOn(deleteOpenSSL)
		dependsOn(deleteLibsignal)
	}


	val prepareLibsignalTask = tasks.register<DownloadFrameworkTask>("prepareLibsignal") {
		description = "Download and unpack libsignal XCFramework."
		frameworkName = "libsignal"
		frameworkVersion = "1.0.0"
		group = "Interop"
		swiftpm.set(false)
		outputs.cacheIf { false }
		projectRootDir = project.rootDir.path
	}

	val prepareOpenSSLTask = tasks.register<DownloadFrameworkTask>("prepareOpenSSL") {
		description = "Downloads and unpack OpenSSL XCFramework."
		frameworkName = "OpenSSL"
		frameworkVersion = "1.1.171-1"
		group = "Interop"
		swiftpm.set(true)
		outputs.cacheIf { false }
		projectRootDir = project.rootDir.path
	}

	if (booleanProperty(Modules.ios.propertyName)) {
		tasks["cinteropLibsignalIosArm64"].dependsOn(prepareLibsignalTask)
		tasks["cinteropOpenSSLIosArm64"].dependsOn(prepareOpenSSLTask)
	}

	if (booleanProperty(Modules.iosSimulator.propertyName)) {
		tasks["cinteropLibsignalIosSimulatorArm64"].dependsOn(prepareLibsignalTask)
		tasks["cinteropOpenSSLIosSimulatorArm64"].dependsOn(prepareOpenSSLTask)
	}
}

