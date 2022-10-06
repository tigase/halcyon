package tigase.halcyon.core.xmpp.modules.auth

import com.soywiz.krypto.sha1
import tigase.halcyon.core.AbstractHalcyon
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



			"bind" {
				xmlns = BIND_XMLNS
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
		if (element.getChildrenNS("bound", BIND_XMLNS) != null) {
			context.modules.getModule<BindModule>(BindModule.TYPE)
				.boundAs(element.getFirstChild("authorization-identifier")!!.value!!)
		}
	}

	private fun processFailure(element: Element) {
	}

	private fun processChallenge(element: Element) {
	}

	fun isAllowed(streamFeatures: Element): Boolean = streamFeatures.getChildrenNS("authentication", XMLNS) != null

}