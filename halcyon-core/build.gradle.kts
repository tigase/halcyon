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
	kotlin("multiplatform") version "1.6.10"
	kotlin("plugin.serialization") version "1.6.10"
}


kotlin {
	jvm {
		compilations.all {
			kotlinOptions.jvmTarget = "1.8"
		}
		withJava()
		testRuns["test"].executionTask.configure {
			useJUnit()
		}
	}
	js(BOTH) {
		browser {
			commonWebpackConfig {
				cssSupport.enabled = true
			}
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}
	ios()

	sourceSets {
		all {
			languageSettings {
				optIn("kotlin.RequiresOptIn")
			}
		}
		val commonMain by getting {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
				implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
				implementation("com.soywiz.korlibs.krypto:krypto:2.4.12")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
		val jvmMain by getting {
			dependencies {
				implementation("org.minidns:minidns-hla:1.0.2")
			}
		}
		val jvmTest by getting {
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
		val jsMain by getting {
			dependencies {
				implementation(kotlin("stdlib-js"))
//				implementation("com.soywiz.korlibs.krypto:krypto-js:1.10.1")
			}
		}
		val jsTest by getting {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}

		val iosMain by getting {
			dependsOn(commonMain)
		}
		val iosTest by getting {
			dependsOn(commonTest)
		}
	}
}