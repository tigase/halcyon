@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")

package tigase.halcyon.core.xmpp.modules.color

import tigase.DummyHalcyon
import tigase.halcyon.core.builder.createConfiguration
import tigase.halcyon.core.xmpp.toBareJID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorGenerationTest {

	@Test
	fun directEngineConfiguration() {
		ColorGenerationModule.configure {
			colorsCache = null
			saturationMin = 80f
			saturationMax = 100f
			lightnessMin = 25f
			lightnessMax = 60f
		}
		ColorGenerationModule.calculateColor("Romeo").let {
			assertEquals(327.25525f, it.h, 0.00001f)
			assertEquals(98.18085f, it.s, 0.00001f)
			assertEquals(56.816483f, it.l, 0.00001f)
		}
	}

	@Test
	fun colorGenerationTest() {
		val halcyon = DummyHalcyon(createConfiguration(false) {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
			install(ColorGenerationModule) {
				colorsCache = null
				saturationMin = 80f
				saturationMax = 100f
				lightnessMin = 25f
				lightnessMax = 60f
			}
		}).apply {
			connect()
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("Romeo").let {
			assertEquals(327.25525f, it.h, 0.00001f)
			assertEquals(98.18085f, it.s, 0.00001f)
			assertEquals(56.816483f, it.l, 0.00001f)
		}
	}

	@Test
	fun colorGenerationGlobalTest() {
		val halcyon = DummyHalcyon(createConfiguration(false) {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
			install(ColorGenerationModule) { }
		}).apply {
			connect()
		}

		ColorGenerationModule.calculateColor("Romeo").let {
			assertEquals(327.25525f, it.h, 0.00001f)
			assertEquals(98.18085f, it.s, 0.00001f)
			assertEquals(56.816483f, it.l, 0.00001f)
		}
	}

	@Test
	fun colorGenerationConstHueSaturation() {
		val halcyon = DummyHalcyon(createConfiguration(false) {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
			install(ColorGenerationModule) {
				saturationMin = 1f
				saturationMax = 1f
				lightnessMin = 0.9f
				lightnessMax = 0.9f
			}
		}).apply {
			connect()
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("Romeo").let {
			assertEquals(327.25525f, it.h, 0.00001f)
			assertEquals(1f, it.s, 0.00001f)
			assertEquals(0.9f, it.l, 0.00001f)
		}
	}

	@Test
	fun colorGenerationConstMaxLowerThanMin() {
		assertFailsWith<AssertionError> {
			val halcyon = DummyHalcyon(createConfiguration(false) {
				auth {
					userJID = "user@example.com".toBareJID()
					password { "pencil" }
				}
				install(ColorGenerationModule) {
					saturationMin = 10f
					saturationMax = 1f
					lightnessMin = 1.9f
					lightnessMax = 0.9f
				}
			}).apply {
				connect()
			}
		}
	}

	@Test
	fun colorGenerationTest2() {
		val halcyon = DummyHalcyon(createConfiguration(false) {
			auth {
				userJID = "user@example.com".toBareJID()
				password { "pencil" }
			}
			install(ColorGenerationModule) {
				hueMin = 0f
				hueMax = 360f
				saturationMin = 0f
				saturationMax = 360f
				lightnessMin = 0f
				lightnessMax = 360f
				colorsCache = NullColorsCache()
			}
		}).apply {
			connect()
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("Romeo").let {
			assertEquals(327.25525f, it.h, 0.00001f)
			assertEquals(327.25525f, it.s, 0.00001f)
			assertEquals(327.25525f, it.l, 0.00001f)
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("juliet@capulet.lit").let {
			assertEquals(209.41040f, it.h, 0.00001f)
			assertEquals(209.41040f, it.s, 0.00001f)
			assertEquals(209.41040f, it.l, 0.00001f)
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("council").let {
			assertEquals(359.9945f, it.h, 0.00001f)
			assertEquals(359.9945f, it.s, 0.00001f)
			assertEquals(359.9945f, it.l, 0.00001f)
		}
		halcyon.getModule(ColorGenerationModule).calculateColor("Board").let {
			assertEquals(171.43066f, it.h, 0.00001f)
			assertEquals(171.43066f, it.s, 0.00001f)
			assertEquals(171.43066f, it.l, 0.00001f)
		}

	}

}