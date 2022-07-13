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
pluginManagement {
	plugins {
		id("com.google.devtools.ksp") version "1.6.21-1.0.6" apply false
//		kotlin("multiplatform") version "1.6.21" apply false
	}
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

rootProject.name = "halcyon"

include(":docs", ":serializer:annotations", ":serializer:plugin", ":halcyon-core", "halcyon-rx", "halcyon-coroutines")
