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
				implementation(project(":halcyon-core"))
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
				implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")
			}
		}
		val jvmTest by getting {
			dependencies {
				implementation(kotlin("test-junit"))

			}
		}
		val jsTest by getting {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}
		val iosTest by getting {
			dependsOn(commonTest)
		}
	}
}