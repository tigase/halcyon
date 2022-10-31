package tigase.halcyon.core.xmpp.modules

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.ConfigurationDSLMarker
import tigase.halcyon.core.modules.AbstractXmppModule
import tigase.halcyon.core.modules.Criterion
import tigase.halcyon.core.modules.XmppModuleProvider
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xmpp.BareJID
import tigase.halcyon.core.xmpp.ErrorCondition
import tigase.halcyon.core.xmpp.XMPPException
import tigase.halcyon.core.xmpp.forms.Field
import tigase.halcyon.core.xmpp.forms.FieldType
import tigase.halcyon.core.xmpp.forms.FormType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.IQType

@ConfigurationDSLMarker
interface InBandRegistrationModuleConfig

class InBandRegistrationModule(context: Context) : InBandRegistrationModuleConfig, AbstractXmppModule(
	context = context, type = TYPE, features = arrayOf(XMLNS), criteria = Criterion.chain(
		Criterion.name(IQ.NAME), Criterion.xmlns(XMLNS)
	)
) {

	companion object : XmppModuleProvider<InBandRegistrationModule, InBandRegistrationModuleConfig> {

		const val XMLNS = "jabber:iq:register"
		override val TYPE = XMLNS
		private const val REGISTRATION_FORM_TYPE = "tigase:private#form_type"

		override fun instance(context: Context): InBandRegistrationModule = InBandRegistrationModule(context)

		override fun configure(module: InBandRegistrationModule, cfg: InBandRegistrationModuleConfig.() -> Unit) =
			module.cfg()

	}

	private val allowedRegistrationFields = listOf(
		"registered",
		"instructions",
		"username",
		"nick",
		"password",
		"name",
		"first",
		"last",
		"email",
		"address",
		"city",
		"state",
		"zip",
		"phone",
		"url",
		"date",
		"misc",
		"text",
		"key",
	)

	override fun process(element: Element) = throw XMPPException(ErrorCondition.NotAcceptable)

	private fun createRegistrationForm(query: Element): JabberDataForm {
		query.getChildrenNS("x", JabberDataForm.XMLNS)
			?.let {
				return JabberDataForm(it)
			}
		val form = JabberDataForm.create(FormType.Form)

		query.children.filter { it.name in allowedRegistrationFields }
			.map { element ->
				Field.create(
					element.name, when (element.name) {
						"password" -> FieldType.TextPrivate
						"registered" -> FieldType.Bool
						else -> FieldType.TextSingle
					}
				)
					.also { field ->
						if (element.name == "registered") {
							field.fieldValue = "1"
						} else {
							field.fieldRequired = true
							field.fieldValue = element.value
						}
					}
			}
			.forEach {
				form.addField(it)
			}

		form.addField(REGISTRATION_FORM_TYPE, FieldType.Hidden).fieldValue = "plain"

		return form
	}

	fun requestRegistrationForm(toJID: BareJID): RequestBuilder<JabberDataForm, IQ> = context.request.iq {
		type = IQType.Get
		to = toJID.toJID()
		query(XMLNS) {}
	}
		.map { iq ->
			iq.getChildrenNS("query", XMLNS)
				?.let(::createRegistrationForm) ?: throw XMPPException(
				ErrorCondition.NotAcceptable, "Missing registration fields."
			)
		}

	fun submitRegistrationForm(toJID: BareJID, form: JabberDataForm): RequestBuilder<Unit, IQ> = context.request.iq {
		type = IQType.Set
		to = toJID.toJID()
		query(XMLNS) {
			if ((form.getFieldByVar(REGISTRATION_FORM_TYPE)?.fieldValue) == "plain") {
				form.getAllFields()
					.filter {
						it.fieldName in allowedRegistrationFields && it.fieldName !in listOf(
							"registered", "instructions"
						)
					}
					.map { element(it.fieldName!!) { +it.fieldValue!! } }
					.forEach {
						addChild(it)
					}
			} else if ((form.getFieldByVar(REGISTRATION_FORM_TYPE)?.fieldValue ?: "form") == "form") {
				form.createSubmitForm()
					.apply {
						children.firstOrNull { it.name == "field" && it.attributes["var"] == REGISTRATION_FORM_TYPE }
							?.let {
								remove(it)
							}
					}
					.let {
						addChild(it)
					}
			}
		}
	}
		.map { }

	fun cancelRegistration(): RequestBuilder<Unit, IQ> = context.request.iq {
		query(XMLNS) {
			"remove" {}
		}
	}
		.map { }

	fun isAllowed(streamFeatures: Element): Boolean =
		context.config.registration != null && streamFeatures.getChildrenNS(
			"register", "http://jabber.org/features/iq-register"
		) != null

}