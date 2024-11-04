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
val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(
        jdkVersion = libs.findVersion("java-languageVersion").get().requiredVersion.toInt()
    )

    jvm {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    js(IR) {
        browser {
            commonWebpackConfig {
				// cssSupport()
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
            binaries.executable()
        }
    }

    listOf(
        // iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            // baseName = "ComposeApp"
            // isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }

        jsMain.dependencies {
            implementation(kotlin("stdlib-js"))
        }

        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }

        iosMain.dependencies {
        }
    }
}
