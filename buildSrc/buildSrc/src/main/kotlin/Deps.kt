object Deps {

	object Badoo {

		object Reaktive {

			private const val VERSION = "1.2.1"
			const val reaktive = "com.badoo.reaktive:reaktive:$VERSION"
			const val reaktiveTesting = "com.badoo.reaktive:reaktive-testing:$VERSION"
			const val utils = "com.badoo.reaktive:utils:$VERSION"
		}
	}

	object JetBrains {

		object Kotlin {

			const val VERSION = "1.6.10"
			const val gradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"
			const val testCommon = "org.jetbrains.kotlin:kotlin-test-common:$VERSION"
			const val testJunit = "org.jetbrains.kotlin:kotlin-test-junit:$VERSION"
			const val testJs = "org.jetbrains.kotlin:kotlin-test-js:$VERSION"
			const val testAnnotationsCommon = "org.jetbrains.kotlin:kotlin-test-annotations-common:$VERSION"
		}

		object KotlinX {

			const val dateTime = "org.jetbrains.kotlinx:kotlinx-datetime:0.3.2"
		}

		object Serialization {

			private const val VERSION = "1.3.2"
			const val core = "org.jetbrains.kotlinx:kotlinx-serialization-core:$VERSION"
			const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:$VERSION"
		}

		object Coroutines {

			private const val VERSION = "1.6.0"
			const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$VERSION"
			const val test = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$VERSION"

		}
	}

	object Soywiz {

		const val krypto = "com.soywiz.korlibs.krypto:krypto:2.4.12"
	}

	object Minidns {

		const val minidns = "org.minidns:minidns-hla:1.0.2"
	}
}