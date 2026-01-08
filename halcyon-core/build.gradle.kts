import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
	id("kotlin-multiplatform-convention")
	id("maven-publish-convention")

	kotlin("plugin.serialization")
	alias(libs.plugins.jetbrains.dokka)
}

val iosApply = { target: KotlinNativeTarget, openSslFrameworkDir: String, libsignalFrameworkDir: String ->
	target.compilations.named("main").configure {
		cinterops {
			val OpenSSL by creating {
				defFile("src/nativeInterop/cinterop/OpenSSL.def")
				includeDirs("$openSslFrameworkDir/OpenSSL.framework/Headers/")
				compilerOpts(
					"-F$openSslFrameworkDir", "-framework", "OpenSSL"
				)
			}
			val libsignal by creating {
				defFile("src/nativeInterop/cinterop/libsignal.def")
				includeDirs("$libsignalFrameworkDir/libsignal.framework/Headers/")
				compilerOpts(
					"-F$libsignalFrameworkDir", "-framework", "libsignal"
				)
			}
		}
	}
}

kotlin {


	iosArm64 {
		iosApply(this, "$rootDir/build/frameworks/OpenSSL.xcframework/ios-arm64_armv7", "$rootDir/build/frameworks/libsignal.xcframework/ios-arm64")
	}

	iosSimulatorArm64 {
		iosApply(this, "$rootDir/build/frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator", "$rootDir/build/frameworks/libsignal.xcframework/ios-arm64_x86_64-simulator")
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

		val jvmMain by getting {
			dependsOn(omemoMain)
			dependencies {
				implementation(libs.minidns)
				implementation(libs.signal.protocol.java)
			}
		}
		val jvmTest by getting

		val jsMain by getting

		val jsTest by getting

		val iosMain by getting {
			dependsOn(omemoMain)
		}
	}
}

dokka {
	moduleName.set("Tigase Halcyon")
	moduleVersion.set(project.version.toString())
	dokkaPublications.html {
		suppressInheritedMembers.set(true)
		failOnWarning.set(false)
		suppressObviousFunctions.set(true)
		suppressInheritedMembers.set(false)
		offlineMode.set(false)
	}

	pluginsConfiguration.html {
		footerMessage.set("(c) Tigase, Inc.")
	}
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

