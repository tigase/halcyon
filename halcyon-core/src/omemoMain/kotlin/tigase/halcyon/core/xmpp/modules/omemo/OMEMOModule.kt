package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.Context
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.requests.Request
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubItemEvent
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.stanzas.IQ
import tigase.halcyon.core.xmpp.stanzas.Message
import tigase.halcyon.core.xmpp.stanzas.MessageNode
import tigase.halcyon.core.xmpp.stanzas.wrap

@HalcyonConfigDsl
/**
 * OMEMO Module configuration.
 */
interface OMEMOModuleConfig {

    /**
     * Specify a store to keep Signal Protocol data like identities, keys, etc.
     */
    var protocolStore: SignalProtocolStore

    /**
     * Store for open OMEMO sessions.
     */
    var sessionStore: OMEMOSessionStore

    var bundleStateStorage: BundleStateStorage

    /**
     * Update identity store, after receiving updated device list notification.
     */
    var autoUpdateIdentityStore: Boolean

    /**
     * Automatically create OMEMO session when a message needs to be encrypted.
     */
    var autoCreateSession: Boolean

}

interface BundleStateStorage {
    fun currentPreKeyId(): Int;
    fun updatePreKeyMax(id: Int);
    fun signedPreKeyId(): Int;
}

/**
 * Module is implementing OMEMO Encryption ([XEP-0384](https://xmpp.org/extensions/attic/xep-0384-0.3.0.html) version 0.3.0).
 */
class OMEMOModule(
    override val context: Context,
    private val pubsubModule: PubSubModule,
) : HalcyonModule, OMEMOModuleConfig {

    /**
     * Module is implementing OMEMO Encryption ([XEP-0384](https://xmpp.org/extensions/attic/xep-0384-0.3.0.html) version 0.3.0).
     */
    companion object : HalcyonModuleProvider<OMEMOModule, OMEMOModuleConfig> {

        const val XMLNS = "eu.siacs.conversations.axolotl"
        override val TYPE = XMLNS

        private const val DEVICE_LIST_NODE: String = "$XMLNS.devicelist"
        private const val BUNDLES_NODE_PREFIX: String = "$XMLNS.bundles:"
        private const val CURRENT: String = "current"

        internal const val ENCRYPTION_ELEMENT = "halcyon:omemo"

        override fun instance(context: Context): OMEMOModule =
            OMEMOModule(context, pubsubModule = context.modules.getModule(PubSubModule))

        override fun configure(module: OMEMOModule, cfg: OMEMOModuleConfig.() -> Unit) {
            module.cfg()
//            try {
//                module.protocolStore
//            } catch (e: UninitializedPropertyAccessException) {
//                throw HalcyonException("The `store` parameter is required in OMEMOModule configuration.")
//            }
//            try {
//                module.sessionStore
//            } catch (e: UninitializedPropertyAccessException) {
//                throw HalcyonException("The `store` parameter is required in OMEMOModule configuration.")
//            }
        }

        override fun requiredModules() = listOf(PubSubModule)

        override fun doAfterRegistration(module: OMEMOModule, moduleManager: ModulesManager) {
//            moduleManager.registerInterceptors(arrayOf(module))
            moduleManager.registerOutgoingFilter(module.outgoingFilter)
            moduleManager.registerIncomingFilter(module.incomingFilter)
        }

    }

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOModule")

    override val type = TYPE
    override val features = arrayOf(XMLNS, "$DEVICE_LIST_NODE+notify")

    override lateinit var protocolStore: SignalProtocolStore
    override var sessionStore: OMEMOSessionStore = InMemoryOMEMOSessionStore()
    override var autoUpdateIdentityStore: Boolean = true
    override var autoCreateSession: Boolean = false
    override lateinit var bundleStateStorage: BundleStateStorage;

    init {
        context.eventBus.register(PubSubItemEvent) {
            if (it is PubSubItemEvent.Published) processPubSubItemPublishedEvent(it)
        }
    }

    private val outgoingFilter = createFilter(::beforeSend)
    private val incomingFilter = createFilter(::afterReceive)

    private fun processPubSubItemPublishedEvent(event: PubSubItemEvent.Published) {
        if (event.pubSubJID != null && event.nodeName == DEVICE_LIST_NODE && event.itemId == CURRENT) {
            processDevicesList(event.pubSubJID, event.content)
        } else if (event.pubSubJID != null && event.nodeName.startsWith(BUNDLES_NODE_PREFIX) && event.itemId == CURRENT) {
            processBundle(event.pubSubJID, event.nodeName, event.content)
        }
    }

    private fun processBundle(jid: JID, bundleId: String, content: Element?) {
        val bundle = content?.toBundleOf(jid.bareJID, bundleId.toInt()) ?: return
        storeBundle(SignalProtocolAddress(bundle.jid.toString(), bundle.deviceId), bundle)
//        println("!!! RECEIVED BUNDLE from $jid | $bundleId | ${content!!.getAsString()}")
//        TODO("Not yet implemented")
    }

    private fun processDevicesList(jid: JID, content: Element?) {
        val deviceList = content.toDeviceList()
        if (autoUpdateIdentityStore) {
            deviceList.forEach {
                val addr = SignalProtocolAddress(jid.toString(), it)
                log.info { "Retrieving OMEMO keys of $jid:$it" }
                if (protocolStore.getIdentity(addr) == null) retrieveBundle(jid.bareJID, it).response {
                    it.onSuccess {
                        storeBundle(addr, it)
                    }
                }.send()
            }
        }
    }

    private fun storeBundle(addr: SignalProtocolAddress, bundle: Bundle) {
        protocolStore.saveIdentity(addr, IdentityKey(bundle.identityKey, 0))
    }

    /**
     * Prepare request to retrieve known devices list of given JabberID.
     * @param jid JabberID
     * @param idFilter item id (`current` by default).
     */
    fun retrieveDevicesIds(jid: BareJID, idFilter: String? = CURRENT): RequestBuilder<List<Int>, IQ> {
        return pubsubModule.retrieveItem(jid = jid, node = DEVICE_LIST_NODE, itemId = idFilter).map { resp ->
            resp.items.map { item ->
                item.content.toDeviceList()
            }.flatten()
        }
    }

    /**
     * Prepare request to retrieve public key bundle of given JabberID.
     * @param jid JabberID
     * @param bundleId requested bundle identifier.
     */
    fun retrieveBundle(jid: BareJID, bundleId: Int): RequestBuilder<Bundle, IQ> {
        return pubsubModule.retrieveItem(jid, "$BUNDLES_NODE_PREFIX$bundleId").map {
            it.items.firstOrNull { it.id == CURRENT }?.content?.toBundleOf(jid, bundleId) ?: throw XMPPException(
                ErrorCondition.ItemNotFound,
                "Bundle $bundleId not found"
            )
        }
    }

    /**
     * Prepare request to delete specific bundle. Works only with own bundles.
     * @param bundleId bundle identifier to delete.
     */
    fun deleteBundle(bundleId: Int): RequestBuilder<Unit, IQ> = pubsubModule.deleteNode(
        jid = context.boundJID?.bareJID ?: throw HalcyonException("Entity not bound"),
        node = "$BUNDLES_NODE_PREFIX$bundleId"
    )

    /**
     * Prepare request to delete whole own devices list.
     */
    fun deleteDeviceList(): RequestBuilder<Unit, IQ> = pubsubModule.deleteNode(
        jid = context.boundJID?.bareJID ?: throw HalcyonException("Entity not bound"), node = DEVICE_LIST_NODE
    )

    /**
     * Prepare request to publish own devices list. It replaces previously published list.
     * @param deviceIds devices list to publish.
     */
    fun publishDeviceList(deviceIds: List<Int>): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
        val payload = element("list") {
            xmlns = XMLNS
            deviceIds.forEach { devId ->
                "device" {
                    attributes["id"] = devId.toString()
                }
            }
        }
        return pubsubModule.publish(
            jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound."),
            node = DEVICE_LIST_NODE,
            itemId = CURRENT,
            payload = payload
        )
    }

    /**
     * Prepare request to publish currently used own key bundle.
     * @param signedPreKeyId id of signed pre-key to publish.
     * @param preKeys list of pre-keys identifiers to publish.
     */
    fun publishOwnBundle(signedPreKeyId: Int, preKeys: List<Int>): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
        return publishBundle(
            protocolStore.getIdentityKeyPair(),
            protocolStore.getLocalRegistrationId(),
            protocolStore.loadSignedPreKey(signedPreKeyId),
            preKeys.map { protocolStore.loadPreKey(it) }
        )
    }

    /**
     * Starts OMEMO session with given JabberID.
     * @param jid JabberID with which the session will be created.
     * @param handler session creation result handler.
     */
    fun startSession(jid: BareJID, handler: (Result<OMEMOSession>) -> Unit) {
        log.finest("retrieving bundles for " + jid.toString() + "...")
        retrieveBundles(listOf(jid, context.boundJID!!.bareJID)) {
            val session = startSession(jid, it)
            if (session != null) handler.invoke(Result.success(session))
            else handler.invoke(Result.failure(HalcyonException("Cannot create OMEMO session")))
        }
    }

    /**
     * Starts OMEMO session with given JabberID.
     * @param jid JabberID with which the session will be created.
     * @param bundles list of previously retrieved key bundles of given JabberID.
     * @return created session or `null`.
     */
    fun startSession(jid: BareJID, bundles: List<Bundle>): OMEMOSession? {
        try {
            log.finest("starting session for jid " + jid.toString() + " with " + bundles.size + "...")
            val myJid = context.boundJID!!.bareJID
            val ciphers = createCiphers(protocolStore, bundles)
            val session = OMEMOSession(protocolStore.getLocalRegistrationId(), myJid, jid, ciphers.toMutableMap())
            sessionStore.storeOMEMOSession(session)
            return session
        } catch (e: Exception) {
            log.warning("got exception while starting session: " + e.message)
            return null
        }
    }

    /**
     * Prepare request to publish currently used own key bundle.
     * @param identityKeyPair Identity key pair.
     * @param registrationId local registration id.
     * @param signedPreKey signed pre-key.
     * @param preKeys list of pre-keys.
     */
    fun publishBundle(
        identityKeyPair: IdentityKeyPair,
        registrationId: Int,
        signedPreKey: SignedPreKeyRecord,
        preKeys: List<PreKeyRecord>
    ): RequestBuilder<PubSubModule.PublishingInfo, IQ> {
        val payload = element("bundle") {
            xmlns = XMLNS
            "signedPreKeyPublic" {
                attributes["signedPreKeyId"] = signedPreKey.getId().toString()
                +signedPreKey.getKeyPair().getPublicKey().serialize().toBase64()
            }
            "signedPreKeySignature" {
                +signedPreKey.getSignature().toBase64()
            }
            "identityKey" {
                +identityKeyPair.getPublicKey().serialize().toBase64()
            }
            "prekeys" {
                preKeys.forEach { prekey ->
                    "preKeyPublic" {
                        attributes["preKeyId"] = prekey.getId().toString()
                        +prekey.getKeyPair().getPublicKey().serialize().toBase64()
                    }
                }
            }
        }
        return pubsubModule.publish(
            jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound."),
            node = "$BUNDLES_NODE_PREFIX$registrationId",
            itemId = CURRENT,
            payload = payload
        )
    }

    private fun afterReceive(element: Element?, chain: StanzaFilterChain) {
        if (element?.name != Message.NAME || element is OMEMOMessage) {
            chain.doFilter(element)
            return
        }
        val senderJid = element.getFromAttr()
        val encElement = element.getChildrenNS("encrypted", XMLNS)
        val senderId =
            encElement?.getFirstChild("header")?.attributes?.get("sid")
                ?.toInt()

        if (senderJid == null || encElement == null || senderId == null) {
            chain.doFilter(element)
            return
        }

        getOMEMOSession(senderJid.bareJID, senderId) {
            it.onSuccess {
                val result = OMEMOEncryptor.decrypt(protocolStore, it, wrap(element))
                if (result.second) {
                    publishBundleIfNeeded();
                }
                chain.doFilter(result.first)
            }
            it.onFailure {
                it.printStackTrace()
                element.remove(encElement)
                element.add(element("body") {
                    +"Cannot decrypt message."
                })
                chain.doFilter(element)
            }
        }
    }

    private var bundleRefreshInProgress = false;

    fun publishBundleIfNeeded() {
        val registrationId = protocolStore.getLocalRegistrationId();
        if (registrationId == 0 || bundleRefreshInProgress) {
            return;
        }

        context.boundJID?.bareJID?.let { jid ->
            bundleRefreshInProgress = true;
            pubsubModule.retrieveItem(jid, "$BUNDLES_NODE_PREFIX$registrationId", itemId = "current").response {
                it.onSuccess {
                    protocolStore.loadSignedPreKeys()?.mapNotNull { it?.getId() }?.max()?.let { signedPreKeyId ->
                        val currentKeys =
                            it.items.firstOrNull()?.content?.getFirstChild("prekeys")?.children?.mapNotNull { it.attributes["preKeyId"]?.toIntOrNull() }
                                ?: emptyList();
                        
                        val validKeys = currentKeys.filter { protocolStore.containsPreKey(it) };
                        val needKeys = 150 - validKeys.size;
                        log.fine("publishing new OMEMO bundle with ${needKeys} new keys")
                        if (needKeys > 0) {
                            val nextPreKeyId = bundleStateStorage.currentPreKeyId() + 1;
                            val newKeys = KeyHelper.generatePreKeys(nextPreKeyId, needKeys).map {
                                protocolStore.storePreKey(it.getId(), it);
                                it.getId()
                            };
                            val preKeysToPublish = (validKeys + newKeys);
                            bundleStateStorage.updatePreKeyMax(preKeysToPublish.max())
                            publishOwnBundle(signedPreKeyId, preKeysToPublish).response {
                                it.onSuccess {
                                    log.info { "publication of new OMEMO bundle succeeded" }
                                    bundleRefreshInProgress = false;
                                }
                                it.onFailure { ex ->
                                    log.warning(ex, { "publication of new OMEMO bundle failed, reverting..." })
                                    newKeys.forEach {
                                        protocolStore.removePreKey(it);
                                    }
                                    bundleStateStorage.updatePreKeyMax(nextPreKeyId - 1)
                                    bundleRefreshInProgress = false;
                                }
                                bundleRefreshInProgress = false;
                            }.send();
                        }
                    }
                }
                it.onFailure {
                    if (it is XMPPError) {
                       when(it.error) {
                           ErrorCondition.InternalServerError, ErrorCondition.ItemNotFound -> protocolStore.loadSignedPreKeys()?.mapNotNull { it?.getId() }?.max()?.let { signedPreKeyId ->
                               val preKeyId = bundleStateStorage.currentPreKeyId();
                               if (preKeyId > 0) {
                                   val newKeys = (0..preKeyId).filter { protocolStore.containsPreKey(it) }
                                   log.fine { "publishing own OMEMO bundle succeeded" }
                                   publishOwnBundle(signedPreKeyId, newKeys).response {
                                       bundleRefreshInProgress = false
                                   }.send();
                               } else {
                                   bundleRefreshInProgress = false;
                               }
                           }
                           else -> {
                               bundleRefreshInProgress = false;
                           }
                       }
                    }
                }
            }.send()
        }
    }

    private fun getOMEMOSession(jid: BareJID, senderId: Int, handler: (Result<OMEMOSession>) -> Unit) {
        val s = sessionStore.getOMEMOSession(jid)
        if (s != null) {
            handler(Result.success(s))
            return
        }
        startSession(jid) {
            it.onSuccess { s ->
                handler(Result.success(s))
            }
            it.onFailure { handler(Result.failure(HalcyonException("Cannot create session"))) }
        }
    }

    private fun encryptAndSend(
        element: Element,
        chain: StanzaFilterChain,
        recipient: FullJID,
        session: OMEMOSession,
        bodyEl: Element,
        order: EncryptMessage
    ) {
        element.remove(bodyEl)

        val encElement = OMEMOEncryptor.encrypt(session, bodyEl.value ?: "")

        element.add(encElement)
        element.add(element("store") {
            xmlns = "urn:xmpp:hints"
        })

        element.clearControls()
        chain.doFilter(element)
    }

    private fun createSessionEncryptAndSend(
        element: Element,
        chain: StanzaFilterChain,
        recipient: FullJID,
        bodyEl: Element,
        order: EncryptMessage
    ) {
        log.finest("startig omemo session with " + recipient.bareJID.toString())
        startSession(recipient.bareJID) {
            print("awaiting omemo session creation....")
            it.onSuccess {
                log.finest("session created successfully!")
                encryptAndSend(element, chain, recipient, it, bodyEl, order)
            }
            it.onFailure {
                log.finest("session creation failed!!")
                it.printStackTrace()
            }
        }
    }

    fun beforeSend(element: Element?, chain: StanzaFilterChain) {
        if (element?.name != Message.NAME) {
            chain.doFilter(element)
            return
        }

        val order = element.encryptionOrder()

        val recipient = element.getToAttr()
        val session = recipient?.let { sessionStore.getOMEMOSession(it.bareJID) }
        val bodyEl = element.getFirstChild("body")


        if (order == EncryptMessage.Yes && session == null && bodyEl != null && recipient != null) {
            if (autoCreateSession) {
                createSessionEncryptAndSend(element, chain, recipient, bodyEl, order)
            } else {
                throw OMEMOException("Can't encrypt the message that needs to be encrypted.")
            }
        } else if (recipient == null || session == null || bodyEl == null || order == EncryptMessage.No) {
            element.clearControls()
            chain.doFilter(element)
        } else {
            encryptAndSend(element, chain, recipient, session, bodyEl, order)
        }
    }

}

enum class EncryptMessage {
    /**
     * Message must be encrypted.
     */
    Yes,

    /**
     * Message must NOT be encrypted.
     */
    No,

    /**
     * Message will be encrypted if OMEMO session already exists, or will be sent unencrypted if session doesn't exist.
     */
    Auto

}

private fun Element?.toDeviceList(): List<Int> =
    if (this?.name == "list" && this.xmlns == OMEMOModule.XMLNS) this.getChildren("device")
        .mapNotNull { it.attributes["id"]?.toInt() } else emptyList()

/**
 * Prepare request to add device to currently published device list.
 * @param deviceId device Id to add.
 */
fun OMEMOModule.addDevice(deviceId: Int): RequestBuilder<Request<PubSubModule.PublishingInfo, IQ>, IQ> {
    val jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound.")
    return retrieveDevicesIds(jid).map { receivedList ->
        val newList = (receivedList + deviceId).distinct()
        publishDeviceList(newList).send()
    }.response {
        it.onFailure {
            publishDeviceList(listOf(deviceId)).send()
        }
    }
}

/**
 * Adds encryption order to message.
 * @param value is `true` then message MUST be encrypted before send. if `false` then message will not be encrypted at all
 */
fun MessageNode.encrypt(value: EncryptMessage = EncryptMessage.Yes) {
    this.element.clearControls()
    element(OMEMOModule.ENCRYPTION_ELEMENT) {
        attributes["encryption"] = value.name
    }
}

private fun Element.encryptionOrder(): EncryptMessage {
    val e = this.getFirstChild(OMEMOModule.ENCRYPTION_ELEMENT) ?: return EncryptMessage.Auto
    return EncryptMessage.valueOf(
        e.attributes["encryption"] ?: throw HalcyonException("Invalid OMEMO control element.")
    )
}

private fun Element.clearControls() {
    this.getChildren(OMEMOModule.ENCRYPTION_ELEMENT).forEach {
        this.remove(it)
    }
}