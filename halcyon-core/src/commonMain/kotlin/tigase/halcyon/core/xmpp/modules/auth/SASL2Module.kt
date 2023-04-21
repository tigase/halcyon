package tigase.halcyon.core.xmpp.modules.auth

import korlibs.crypto.sha1
import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.connector.SessionController
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModule
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

@HalcyonConfigDsl
interface SASL2ModuleConfig : SASLModuleConfig

class SASL2Module(override val context: Context, private val discoveryModule: DiscoveryModule) : XmppModule,
																								 SASL2ModuleConfig {

	private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.auth.SASL2Module")

	companion object : XmppModuleProvider<SASL2Module, SASL2ModuleConfig> {

		const val XMLNS = "urn:xmpp:sasl:2"
		override val TYPE = "tigase.halcyon.core.xmpp.modules.auth.SASL2Module"
		override fun configure(module: SASL2Module, cfg: SASL2ModuleConfig.() -> Unit) = module.cfg()

		override fun instance(context: Context): SASL2Module =
			SASL2Module(context, discoveryModule = context.modules.getModule(DiscoveryModule))

		override fun requiredModules() = listOf(DiscoveryModule)
	}

	override val type = TYPE
	override val criteria = Criterion.or(
		Criterion.nameAndXmlns("success", XMLNS),
		Criterion.nameAndXmlns("failure", XMLNS),
		Criterion.nameAndXmlns("challenge", XMLNS)
	)
	override val features: Array<String>? = null

	private val engine = SASLEngine(context)

	override var enabled: Boolean = true

	override fun initialize() {
		engine.add(SASLScramSHA256())
		engine.add(SASLScramSHA1())
		engine.add(SASLPlain())
	}

	fun startAuth(streamFeatures: Element) {
		val saslStreamFeatures = streamFeatures.getChildrenNS("authentication", XMLNS)
			?: throw HalcyonException("No SASL2 features in stream.")
		val allowedMechanisms = saslStreamFeatures.children.filter { it.name == "mechanism" }
			.mapNotNull { it.value }
		val authData = engine.start(allowedMechanisms)
		val authElement = element("authenticate") {
			xmlns = XMLNS
			attribute("mechanism", authData.mechanismName)
			"initial-response" {
				if (authData.data != null) +authData.data
			}
			"user-agent" {
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
		}

		context.writer.writeDirectly(authElement)
	}

	override fun process(element: Element) {
		try {
			when (element.name) {
				"success" -> processSuccess(element)
				"failure" -> processFailure(element)
				"challenge" -> processChallenge(element)
				else -> throw XMPPException(ErrorCondition.BadRequest, "Unsupported element")
			}
		} catch (e: ClientSaslException) {
			engine.saslContext.state = State.Failed
			context.eventBus.fire(SASLEvent.SASLError(SASLModule.SASLError.Unknown, e.message))
		}
	}

	private fun processSuccess(element: Element) {
		engine.evaluateSuccess(element.getFirstChild("additional-data")?.value)
		try {
			InlineResponse(InlineProtocolStage.AfterSasl, element).let { response ->
				context.modules.getModules()
					.filterIsInstance<InlineProtocol>()
					.forEach { consumer ->
						consumer.process(response)
					}
			}
		} catch (e: Throwable) {
			log.log(Level.SEVERE, "Error during inline processing: ${e.message}", e)
			context.eventBus.fire(SessionController.SessionControllerEvents.ErrorStop("Error during inline processing: ${e.message}"))
		}
	}

	private fun processFailure(element: Element) {
		val errElement = element.getFirstChild()!!
		val saslError = SASLModule.SASLError.valueByElementName(errElement.name)!!

		var errorText: String? = null
		element.getFirstChild("text")
			?.apply {
				errorText = this.value
			}
		engine.evaluateFailure(saslError, errorText)
	}

	private fun processChallenge(element: Element) {
		val v = element.value
		val r = engine.evaluateChallenge(v)

		val authElement = element("response") {
			xmlns = XMLNS
			if (r != null) +r
		}
		context.writer.writeDirectly(authElement)
	}

	fun isAllowed(streamFeatures: Element): Boolean =
		context.config.sasl != null && enabled && streamFeatures.getChildrenNS("authentication", XMLNS) != null

}