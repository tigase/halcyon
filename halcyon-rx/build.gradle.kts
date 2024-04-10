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
			commonWebpackConfig {
//				cssSupport.enabled = true
			}
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}
	ios {}
	iosSimulatorArm64 {}
	sourceSets {
		named("commonMain") {
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation(project(":halcyon-core"))
				implementation(deps.reactive.reaktive)
			}
		}
		named("commonTest") {
			dependencies {
				implementation(kotlin("test"))
				implementation(deps.reactive.testing)
			}
		}
	}
}