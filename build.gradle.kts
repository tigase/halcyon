import java.util.*

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
import net.researchgate.release.ReleaseExtension

plugins {
	`maven-publish`
	id("net.researchgate.release") version "3.0.2"
}

configure<ReleaseExtension> {
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

tasks["afterReleaseBuild"].dependsOn("publish")

buildscript {
	repositories {
		google()
		mavenCentral()
		mavenLocal()
		jcenter()
	}
	dependencies {
		classpath(deps.kotlin.kotlinGradlePlug)
		classpath(deps.kotlinx.serialization.gradlePlug)
		classpath(deps.kotlin.dokkaPlug)
	}
}

repositories {
	mavenCentral()
	jcenter()
	gradlePluginPortal()
}

allprojects {
	group = "tigase.halcyon"
	version = findProperty("version").toString()

}

subprojects {
	repositories {
		mavenCentral()
		mavenLocal()
		maven(url = findProperty("tigaseMavenRepoRelease").toString())
		maven(url = findProperty("tigaseMavenRepoSnapshot").toString())
		jcenter()
	}
	pluginManager.withPlugin("maven-publish") {

		publishing {
			val props = Properties().also { props ->
					val file = File("local.properties")
					if (file.exists()) file.reader()
						.use { props.load(it) }
				}
			repositories {
				maven {
					url = if (project.version.toString()
							.endsWith("-SNAPSHOT", ignoreCase = true)
					) {
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