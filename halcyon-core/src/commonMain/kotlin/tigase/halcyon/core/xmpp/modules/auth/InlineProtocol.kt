package tigase.halcyon.core.xmpp.modules.auth

import tigase.halcyon.core.xml.Element

enum class InlineProtocolStage {

	AfterSasl,
	AfterBind
}

interface InlineProtocol {

	fun featureFor(features: InlineFeatures, stage: InlineProtocolStage): Element?

	fun process(response: InlineResponse)

}

data class InlineResponse(val stage: InlineProtocolStage, val element: Element)

data class InlineFeatures(val features: List<Element>) {

	companion object {

		fun create(element: Element): InlineFeatures {
			val features = element.getFirstChild("inline")?.children ?: emptyList()
			return InlineFeatures(features)
		}

	}

	fun supports(name: String, xmlns: String): Boolean = features.any { it.name == name && it.xmlns == xmlns }

	fun supports(featureXmlns: String): Boolean =
		features.any { it.name == "feature" && it.attributes["var"] == featureXmlns }

	fun subInline(name: String, xmlns: String): InlineFeatures {
		val features = features.find { it.name == name && it.xmlns == xmlns }
			?.getFirstChild("inline")?.children ?: emptyList()
		return InlineFeatures(features)
	}

}

fun InlineResponse.whenExists(stage: InlineProtocolStage, name: String, xmlns: String, handler: (Element) -> Unit) {
	if (stage == this.stage) {
		this.element.children.find { it.name == name && it.xmlns == xmlns }
			?.let {
				handler.invoke(it)
			}
	}
}

fun InlineResponse.whenNotExists(stage: InlineProtocolStage, name: String, xmlns: String, handler: () -> Unit) {
	if (stage == this.stage) {
		if (!this.element.children.any { it.name == name && it.xmlns == xmlns }) {
			handler.invoke()
		}

	}
}