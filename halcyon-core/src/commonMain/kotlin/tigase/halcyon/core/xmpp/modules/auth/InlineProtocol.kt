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

class InlineResponseHandler(
	private val response: InlineResponse,
	private val stage: InlineProtocolStage,
	private val name: String,
	private val xmlns: String,
) {

	internal fun ifExists(handler: (Element) -> Unit) {
		if (stage == response.stage) {
			response.element.children.find { it.name == name && it.xmlns == xmlns }
				?.let {
					handler.invoke(it)
				}
		}
	}

	internal fun ifNotExists(handler: () -> Unit) {
		if (stage == response.stage) {
			if (!response.element.children.any { it.name == name && it.xmlns == xmlns }) {
				handler.invoke()
			}
		}
	}

}

//fun InlineResponse.whenExists(
//	stage: InlineProtocolStage,
//	name: String,
//	xmlns: String,
//	handler: (Element) -> Unit,
//): InlineResponseHandler {
//	val r = InlineResponseHandler(this, stage, name, xmlns)
//	r.ifExists(handler)
//	return r
//}
//
//infix fun InlineResponseHandler.ifNotExists(handler: () -> Unit) {
//	this.ifNotExists(handler)
//}

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