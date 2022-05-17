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
	id("multiplatform-setup")
	id("publishing-setup")
	kotlin("plugin.serialization") version Deps.JetBrains.Kotlin.VERSION
}


kotlin {
	ios {
		// TODO: Before compilation you need to download https://github.com/tigase/openssl-swiftpm/releases/download/1.1.171/OpenSSL.xcframework.zip to "frameworks" directory and unpack this ZIP file.
		// TODO: Before compilation it is required to go to OpenSSL.xcframework to each subdirectory and Headers and move all files there to "openssl" subdirectory inside Headers
		compilations.getByName("main") {
			val frameworkDir = if (System.getenv("SDK_NAME")
					?.startsWith("iphoneos") == true
			) {
				"$projectDir/../frameworks/OpenSSL.xcframework/ios-arm64_armv7"
			} else {
				"$projectDir/../frameworks/OpenSSL.xcframework/ios-arm64_i386_x86_64-simulator"
			}

			val OpenSSL by cinterops.creating {
				defFile("src/nativeInterop/cinterop/OpenSSL.def")
				includeDirs("$frameworkDir/")
				compilerOpts(
					"-F$frameworkDir",
					"-framework",
					"OpenSSL"
				)
			}
			kotlinOptions.freeCompilerArgs = listOf(
				"-include-binary", "$frameworkDir/OpenSSL.framework/OpenSSL"
			)
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
			binaries.getTest("DEBUG")
				.apply {
					linkerOpts(
						"-rpath", frameworkDir
					)
				}
		}
		binaries {
			staticLib {
			}
		}
		compilations["main"].enableEndorsedLibs = true
	}

	sourceSets {
		commonMain {
			dependencies {
				implementation(Deps.JetBrains.Serialization.core)
				implementation(Deps.JetBrains.KotlinX.dateTime)
				implementation(Deps.Soywiz.krypto)
			}
		}
		jvmMain {
			dependencies {
				implementation(Deps.Minidns.minidns)
			}
		}
		named("iosMain") { }
		named("iosTest") { }
	}
}
