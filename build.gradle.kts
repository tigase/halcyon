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
plugins {
	`maven-publish`
}

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
	}
}

repositories {
	mavenCentral()
	jcenter()
	gradlePluginPortal()
}

allprojects {
	group = "tigase.halcyon"
	version = findProperty("halcyonVersion").toString()

}

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

subprojects {
	repositories {
		mavenCentral()
		mavenLocal()
		maven(url = findProperty("tigaseMavenRepoRelease").toString())
		maven(url = findProperty("tigaseMavenRepoSnapshot").toString())
		jcenter()
	}

}