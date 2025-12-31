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
    `maven-publish`
}

pluginManager.withPlugin("maven-publish") {
    publishing {
        repositories {
            maven {
                val releasesRepoUrl = project.properties["tigaseMavenRepoRelease"]
                val snapshotsRepoUrl = project.properties["tigaseMavenRepoSnapshot"]
                val repoUrl = if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                this.url = uri(repoUrl!!)

                credentials {
                    username = project.properties["mavenUsername"] as String
                    password = project.properties["mavenPassword"] as String
                }
            }
        }
    }
}
