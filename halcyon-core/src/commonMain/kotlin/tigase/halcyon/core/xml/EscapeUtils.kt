package tigase.halcyon.core.xml

class EscapeUtils private constructor() {

	companion object {
		private val ENTITIES = arrayOf(arrayOf("&", "&amp;"), arrayOf("<", "&lt;"), arrayOf(">", "&gt;"),
									   arrayOf("\"", "&quot;"), arrayOf("'", "&apos;"))

		fun escape(str: String?): String? {
			var v: String? = str ?: return null
			if (v!!.isEmpty()) {
				return v
			}
			for (i in ENTITIES.indices) {
				v = v!!.replace(ENTITIES[i][0], ENTITIES[i][1])
			}
			return v
		}

		fun unescape(str: String?): String? {
			var v: String? = str ?: return null
			if (v!!.isEmpty()) {
				return v
			}
			for (i in ENTITIES.indices.reversed()) {
				v = v!!.replace(ENTITIES[i][1], ENTITIES[i][0])
			}
			return v
		}
	}

}