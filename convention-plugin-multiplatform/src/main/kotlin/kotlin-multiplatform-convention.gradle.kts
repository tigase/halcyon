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

import java.time.Duration

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(
        jdkVersion = libs.findVersion("java-languageVersion").get().requiredVersion.toInt()
    )

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // default targets
    jvm() {}
    iosArm64() {}
    iosSimulatorArm64() {}

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


    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.ExperimentalMultiplatform")
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}


// #### Tests

// fails to run…
project.gradle.startParameter.excludedTaskNames.add("iosSimulatorArm64Test")

// can't be run without additional dependencies…
project.gradle.startParameter.excludedTaskNames.add("jsBrowserTest")


tasks.withType<Test> {
    timeout.set(Duration.ofSeconds(10))

    testLogging {
        events("skipped", "failed")
    }
}