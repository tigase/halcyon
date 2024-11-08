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
	alias(libs.plugins.multiplatform)
	`maven-publish`
	signing
}

kotlin {
	jvmToolchain(jdkVersion = libs.versions.java.languageVersion.get().toInt())
	jvm {
		withJava()
		testRuns["test"].executionTask.configure {
			useJUnit()
		}
	}
	js(IR) {
		browser {
			commonWebpackConfig {
//				cssSupport()
			}
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}
	iosArm64 {}
	iosSimulatorArm64 {}
	sourceSets {
		named("commonMain") {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation(project(":halcyon-core"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
		named("commonTest") {
			dependencies {
				implementation(kotlin("test"))
				implementation(libs.kotlinx.coroutines.test)
			}
		}
	}
}