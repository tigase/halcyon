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
	kotlin("multiplatform") version "1.4.10"
	kotlin("plugin.serialization") version "1.4.10"
	id("maven-publish")
	id("org.asciidoctor.jvm.convert") version "3.1.0"
}

group = "tigase.halcyon"
version = findProperty("halcyonVersion").toString()

repositories {
	mavenCentral()
	mavenLocal()
	maven(url = findProperty("tigaseMavenRepoRelease").toString())
	maven(url = findProperty("tigaseMavenRepoSnapshot").toString())
	jcenter()
}

publishing {
	repositories {
		maven {
			url = if (project.version.toString().endsWith("-SNAPSHOT", ignoreCase = true)) {
				uri(findProperty("tigaseMavenRepoSnapshot").toString())
			} else {
				uri(findProperty("tigaseMavenRepoRelease").toString())
			}
			credentials {
				username = findProperty("mavenUsername").toString()
				password = findProperty("mavenPassword").toString()
			}
		}
	}
}

kotlin {
	jvm() // Creates a JVM target with the default name 'jvm'
	js(BOTH) {
		browser {
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
	}

	sourceSets {
		all {
			languageSettings.useExperimentalAnnotation("org.mylibrary.OptInAnnotation")
		}
		val commonMain by getting {
			languageSettings.apply {
				languageVersion = "1.3" // possible values: '1.0', '1.1', '1.2', '1.3'
				apiVersion = "1.3" // possible values: '1.0', '1.1', '1.2', '1.3'
			}
			dependencies {
				implementation(kotlin("stdlib-common"))
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
				implementation("com.soywiz.korlibs.krypto:krypto:2.0.0-rc3")
			}
		}
		val commonTest by getting {
			dependencies {
				implementation(kotlin("test-common"))
				implementation(kotlin("test-annotations-common"))
			}
		}
		jvm().compilations["main"].defaultSourceSet {
			dependencies {
				implementation(kotlin("stdlib-jdk8"))
//				implementation("com.soywiz.korlibs.krypto:krypto-jvm:1.10.1")
				implementation("org.minidns:minidns-hla:0.3.1")
			}
		}
		jvm().compilations["test"].defaultSourceSet {
			dependencies {
				implementation(kotlin("test-junit"))
			}
		}
		js().compilations["main"].defaultSourceSet {
			dependencies {
				implementation(kotlin("stdlib-js"))
//				implementation("com.soywiz.korlibs.krypto:krypto-js:1.10.1")
			}
		}
		js().compilations["test"].defaultSourceSet {
			dependencies {
				implementation(kotlin("test-js"))
			}
		}

	}
}

asciidoctorj {
	modules {
		diagram.use()
	}
}

tasks {
	asciidoctor {
		baseDirFollowsSourceDir()
		setSourceDir(file("src/docs/asciidoc"))
		sources(delegateClosureOf<PatternSet> {
			include("index.asciidoc")
		})
		options(mapOf("doctype" to "book", "ruby" to "erubis"))
		attributes(mapOf("source-highlighter" to "coderay", "toc" to "", "idprefix" to "", "idseparator" to "-"))
	}
}