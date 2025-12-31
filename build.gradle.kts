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
	kotlin("multiplatform") version libs.versions.jetbrains.kotlin apply false
	kotlin("plugin.serialization") version libs.versions.jetbrains.kotlin apply false

	id("maven-publish-convention")

	// run `./gradlew build taskTree --no-repeat`
	alias(libs.plugins.task.tree.plugin)

	// handling release https://github.com/researchgate/gradle-release
	alias(libs.plugins.researchgate.release)
}

allprojects {
	group = "tigase.halcyon"
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

tasks["afterReleaseBuild"].dependsOn("publish")
