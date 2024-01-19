package tigase.halcyon.core.xmpp.modules.color

import korlibs.crypto.sha1
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.modules.HalcyonModule
import tigase.halcyon.core.modules.HalcyonModuleProvider
import kotlin.math.absoluteValue

/**
 * Color in HSL representation.
 */
data class Color(val h: Float, val s: Float, val l: Float)

interface ColorsCache {

	fun store(key: Any, color: Color)

	fun get(key: Any): Color?
	fun remove(key: Any)

	fun clear()

}

@HalcyonConfigDsl
interface ColorGenerationModuleConfig {

	/**
	 * Minimum allowed Hue.
	 */
	var hueMin: Float

	/**
	 * Maximum allowed Hue.
	 */
	var hueMax: Float

	/**
	 * Minimum allowed Saturation.
	 */
	var saturationMin: Float

	/**
	 * Maximum allowed Saturation.
	 */
	var saturationMax: Float

	/**
	 * Minimum allowed Lightness.
	 */
	var lightnessMin: Float

	/**
	 * Maximum allowed Lightness.
	 */
	var lightnessMax: Float

	/**
	 * Calculated colors cache.
	 */
	var colorsCache: ColorsCache?

}

/**
 * Module is implementing Consistent Color Generation ([XEP-0392](https://xmpp.org/extensions/xep-0392.html)).
 *
 */
class ColorGenerationModule(override val context: Context) : ColorGeneratorEngine(), HalcyonModule {

	override val type = TYPE
	override val features = null

	/**
	 * Module is implementing Consistent Color Generation ([XEP-0392](https://xmpp.org/extensions/xep-0392.html)).
	 *
	 */
	companion object : HalcyonModuleProvider<ColorGenerationModule, ColorGenerationModuleConfig> {

		private val engine: ColorGeneratorEngine = ColorGeneratorEngine()

		override val TYPE = "https://xmpp.org/extensions/xep-0392.html"

		override fun instance(context: Context): ColorGenerationModule = ColorGenerationModule(context)

		override fun configure(module: ColorGenerationModule, cfg: ColorGenerationModuleConfig.() -> Unit) {
			module.config(cfg)
		}

		fun configure(cfg: ColorGenerationModuleConfig.() -> Unit) {
			engine.config(cfg)
		}

		fun calculateColor(jid: Any): Color = engine.calculateColor(jid)
	}

}

/**
 * No colors cache.
 */
class NullColorsCache : ColorsCache {

	override fun store(key: Any, color: Color) {}

	override fun get(key: Any): Color? = null

	override fun remove(key: Any) {}

	override fun clear() {}
}


/**
 * Default, in-memory colors cache.
 */
class InMemoryColorsCache : ColorsCache {

	private val cache = mutableMapOf<Any, Color>()

	override fun store(key: Any, color: Color) {
		cache.put(key, color)
	}

	override fun get(key: Any): Color? = cache[key]

	override fun remove(key: Any) {
		cache.remove(key)
	}

	override fun clear() {
		cache.clear()
	}
}

open class ColorGeneratorEngine : ColorGenerationModuleConfig {

	override var hueMin: Float = 0F
	override var hueMax: Float = 360F

	override var saturationMin: Float = 80f
	override var saturationMax: Float = 100f

	override var lightnessMin: Float = 25f
	override var lightnessMax: Float = 60f

	override var colorsCache: ColorsCache? = InMemoryColorsCache()

	private fun normalizeHash(hash: Float, min: Float, max: Float) = (hash.absoluteValue * (max - min)) + min

	private fun calculateValue(data: String): Float = data.encodeToByteArray().sha1().bytes.take(2).reversed()
		.fold(0L) { acc, byte -> acc.shl(8).or(byte.toUByte().toLong()) } / 65536.toFloat()

	fun config(cfg: ColorGenerationModuleConfig.() -> Unit) {
		this.cfg()
		colorsCache?.clear()
		checkSettings()
	}

	fun checkSettings() {
		if (hueMin > hueMax) throw AssertionError("hueMin cannot be greater than hueMax.")
		if (saturationMin > saturationMax) throw AssertionError("saturationMin cannot be greater than saturationMax.")
		if (lightnessMin > lightnessMax) throw AssertionError("lightnessMin cannot be greater than lightnessMax.")
	}

	fun calculateColor(jid: Any): Color {
		val key = jid.toString()

		val cachedColor = colorsCache?.get(key)
		return if (cachedColor != null) {
			cachedColor
		} else {
			val hash = calculateValue(key)
			val c = Color(
				h = normalizeHash(hash, hueMin, hueMax),
				s = normalizeHash(hash, saturationMin, saturationMax),
				l = normalizeHash(hash, lightnessMin, lightnessMax)
			)
			colorsCache?.store(key, c)
			c
		}
	}

}