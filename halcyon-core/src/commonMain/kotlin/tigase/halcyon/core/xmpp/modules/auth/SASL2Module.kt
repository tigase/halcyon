package tigase.halcyon.core.xmpp.modules.auth

import com.soywiz.krypto.sha1
import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

class SASL2Module(override val context: AbstractHalcyon) : XmppModule {

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASL2Module")

	companion object {

		const val XMLNS = "urn:xmpp:sasl:2"
		const val TYPE = "tigase.halcyon.core.xmpp.modules.auth.SASL2Module"

		const val BIND_XMLNS = "urn:xmpp:bind:0"

	}

	override val type = TYPE
	override val criteria = Criterion.or(
		Criterion.nameAndXmlns("success", XMLNS),
		Criterion.nameAndXmlns("failure", XMLNS),
		Criterion.nameAndXmlns("challenge", XMLNS)
	)
	override val features: Array<String>? = null

	private val engine = SASLEngine(context)

	override fun initialize() {
		engine.add(SASLPlain())
		engine.add(SASLAnonymous())
	}

	fun startAuth(streamFeatures: Element) {
		val saslStreamFeatures = streamFeatures.getChildrenNS("authentication", XMLNS)
			?: throw HalcyonException("No SASL2 features in stream.")
		val authData = engine.start()
		val authElement = element("authenticate") {
			xmlns = XMLNS
			attribute("mechanism", authData.mechanismName)
			"initial-response" {
				if (authData.data != null) +authData.data
			}
			"user-agent" {
				val discoveryModule = context.getModule<DiscoveryModule>(DiscoveryModule.TYPE)
				val softwareName = discoveryModule.clientName
				val deviceName = getDeviceName()
				attributes["id"] = "$softwareName:$deviceName".encodeToByteArray()
					.sha1().hex
				"software" { +softwareName }
				"device" { +deviceName }
			}

			val saslInlineFeatures = InlineFeatures.create(saslStreamFeatures)
			context.modules.getModules()
				.filterIsInstance<InlineProtocol>()
				.mapNotNull { it.featureFor(saslInlineFeatures, InlineProtocolStage.AfterSasl) }
				.forEach { addChild(it) }

			val bindInlineFeatures = saslInlineFeatures.subInline("bind", BIND_XMLNS)
			"bind" {
				xmlns = BIND_XMLNS
				"tag" { +"Halcyon" }
				context.modules.getModules()
					.filterIsInstance<InlineProtocol>()
					.mapNotNull { it.featureFor(bindInlineFeatures, InlineProtocolStage.AfterBind) }
					.forEach { addChild(it) }
			}
		}

		context.writer.writeDirectly(authElement)
	}

	override fun process(element: Element) {
		when (element.name) {
			"success" -> processSuccess(element)
			"failure" -> processFailure(element)
			"challenge" -> processChallenge(element)
			else -> throw XMPPException(ErrorCondition.BadRequest, "Unsupported element")
		}
	}

	private fun processSuccess(element: Element) {

		engine.evaluateSuccess(null)
		InlineResponse(InlineProtocolStage.AfterSasl, element).let { response ->
			context.modules.getModules()
				.filterIsInstance<InlineProtocol>()
				.forEach { consumer ->
					consumer.process(response)
				}
		}

		element.getChildrenNS("bound", BIND_XMLNS)
			?.let { boundElement ->
				context.modules.getModule<BindModule>(BindModule.TYPE)
					.boundAs(element.getFirstChild("authorization-identifier")!!.value!!)
				InlineResponse(InlineProtocolStage.AfterBind, boundElement).let { response ->
					context.modules.getModules()
						.filterIsInstance<InlineProtocol>()
						.forEach { consumer ->
							consumer.process(response)
						}
				}
			}

	}

	private fun processFailure(element: Element) {
	}

	private fun processChallenge(element: Element) {
		val v = element.value
		val r = engine.evaluateChallenge(v)

		val authElement = element("response") {
			xmlns = SASLModule.XMLNS
			if (r != null) +r
		}
		context.writer.writeDirectly(authElement)
	}

	fun isAllowed(streamFeatures: Element): Boolean = streamFeatures.getChildrenNS("authentication", XMLNS) != null

}