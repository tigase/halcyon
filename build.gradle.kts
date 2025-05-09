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
import java.util.*

plugins {
	`maven-publish`
	signing
	alias(libs.plugins.researchgate.release)
	alias(libs.plugins.multiplatform).apply(false)
}

allprojects {
	apply(from = "$rootDir/ktlint.gradle")
}

configure<net.researchgate.release.ReleaseExtension> {
	ignoredSnapshotDependencies.set(listOf("net.researchgate:gradle-release"))

	versionPropertyFile.set("gradle.properties")
	versionProperties.set(listOf("version", "halcyonVersion"))

	preTagCommitMessage.set("Release version: ")
	tagCommitMessage.set("Tag version: ")
	newVersionCommitMessage.set("Bump version: ")


	// Fail the release process when there are un-committed changes. Will commit files automatically if set to false.
	failOnCommitNeeded.set(true)
	failOnUnversionedFiles.set(true)
	failOnPublishNeeded.set(false)
	failOnSnapshotDependencies.set(false)
	failOnUpdateNeeded.set(true)


	with(git) {
		requireBranch.set("master")
	}
}

val props = Properties().also { props ->
	val file = File("local.properties")
	if (file.exists()) file.reader().use { props.load(it) }
}

tasks["afterReleaseBuild"].dependsOn("publish")

allprojects {
	group = "tigase.halcyon"
	version = insertBuildNumber(findProperty("version").toString(), "git rev-list --count HEAD".runCommand(File("./")))
}

project.gradle.startParameter.excludedTaskNames.add("jsBrowserTest")

fun insertBuildNumber(version: String, build: String): String {
	return version
//	if (!version.contains("-SNAPSHOT")) return "$version.$build"
//	return version.substringBefore("-SNAPSHOT") + ".$build"
}

subprojects {
	repositories {
		mavenCentral()
		mavenLocal()
		maven(url = findProperty("tigaseMavenRepoRelease").toString())
		maven(url = findProperty("tigaseMavenRepoSnapshot").toString())
	}
	pluginManager.withPlugin("signing") {
		val secretKey = props["signing.secretKey"]?.toString()
		val password = props["signing.password"]?.toString()
		if (!secretKey.isNullOrBlank()) {
			logger.info("Signing is enabled.")
			signing {
				useInMemoryPgpKeys(secretKey, password)
				sign(publishing.publications)
			}
		} else {
			logger.info("Signing is disabled.")
		}
	}
	pluginManager.withPlugin("maven-publish") {
		publishing {
			repositories {
				maven {
					url = if (project.version.toString().endsWith("-SNAPSHOT", ignoreCase = true)) {
						uri(findProperty("tigaseMavenRepoSnapshot").toString())
					} else {
						uri(findProperty("tigaseMavenRepoRelease").toString())
					}
					credentials {
						username = props["mavenUsername"]?.toString() ?: findProperty("mavenUsername").toString()
						password = props["mavenPassword"]?.toString() ?: findProperty("mavenPassword").toString()
					}
				}
			}
		}

	}
}


fun String.runCommand(
	workingDir: File = File("."), timeoutAmount: Long = 60, timeoutUnit: TimeUnit = TimeUnit.SECONDS,
): String = ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex())).directory(workingDir)
	.redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE).start()
	.apply { waitFor(timeoutAmount, timeoutUnit) }.run {
		val error = errorStream.bufferedReader().readText().trim()
		if (error.isNotEmpty()) {
			println("Cannot determine build number.")
			"0"
		} else inputStream.bufferedReader().readText().trim()
	}