plugins {
	`maven-publish`
}

publishing {
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
				username = findProperty("mavenUsername").toString()
				password = findProperty("mavenPassword").toString()
			}
		}
	}
}