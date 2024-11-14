package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.ClearedEvent
import tigase.halcyon.core.Context
import tigase.halcyon.core.Scope
import tigase.halcyon.core.builder.HalcyonConfigDsl
import tigase.halcyon.core.exceptions.HalcyonException
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.modules.*
import tigase.halcyon.core.requests.RHandler
import tigase.halcyon.core.requests.RequestBuilder
import tigase.halcyon.core.requests.XMPPError
import tigase.halcyon.core.toBase64
import tigase.halcyon.core.utils.Lock
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.*
import tigase.halcyon.core.xmpp.forms.Field
import tigase.halcyon.core.xmpp.forms.FormType
import tigase.halcyon.core.xmpp.forms.JabberDataForm
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

        private val PUBSUB_CONFIG_OPEN_ACCESS_MODEL = JabberDataForm.create(FormType.Form).apply {
            addField("pubsub#access_model", null).fieldValue = "open"
        }

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

            module.initialize();
        }

    }

    private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo.OMEMOModule")

    override val type = TYPE
    override val features = arrayOf(XMLNS, "$DEVICE_LIST_NODE+notify")

    override lateinit var protocolStore: SignalProtocolStore
//    override var sessionStore: OMEMOSessionStore = InMemoryOMEMOSessionStore()
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

    private var devices: MutableMap<BareJID,List<Int>> = mutableMapOf();
    private var devicesFetchError: MutableMap<BareJID,List<Int>> = mutableMapOf();

    private fun initialize() {
        context.eventBus.register(ClearedEvent) {
            if (it.scopes.contains(Scope.Session)) {
                log.fine { "resetting list of known OMEMO devices" }
                devices.clear();
                devicesFetchError.clear();
            }
        }
    }

    private fun processPubSubItemPublishedEvent(event: PubSubItemEvent.Published) {
        if (event.pubSubJID != null && event.nodeName == DEVICE_LIST_NODE && event.itemId == CURRENT) {
            processDevicesList(event.pubSubJID, event.content)
        } else if (event.pubSubJID != null && event.nodeName.startsWith(BUNDLES_NODE_PREFIX) && event.itemId == CURRENT) {
            val deviceId = event.nodeName.substringAfter(BUNDLES_NODE_PREFIX);
            processBundle(event.pubSubJID, deviceId, event.content)
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
            if (context.boundJID?.bareJID == jid.bareJID) {
                val knownSessions = protocolStore.getSubDeviceSessions(jid.bareJID.toString());
                deviceList.filterNot(knownSessions::contains).forEach { deviceId ->
                    startSession(jid.bareJID, deviceId) {
                        it.onFailure {
                            log.warning(it,{ "starting session for ${jid} with device ${deviceId} failed"})
                        }
                        it.onSuccess {
                            log.info("started session for ${jid} with device ${deviceId} successfully")
                        }
                    }
                }

                val localDeviceId = protocolStore.getLocalRegistrationId();
                if (!deviceList.contains(localDeviceId)) {
                    publishDeviceList(deviceList + listOf(localDeviceId)).response {
                        it.onFailure {
                            log.warning(it, { "failed to publish updated device list" })
                        }
                        it.onSuccess {
                            log.fine("published updated device list successfully")
                        }
                    }.send();
                }
            } else {
                devices[jid.bareJID] = deviceList;
            }
//            deviceList.forEach {
//                val addr = SignalProtocolAddress(jid.toString(), it)
//                log.info { "Retrieving OMEMO keys of $jid:$it" }
//                if (protocolStore.getIdentity(addr) == null) retrieveBundle(jid.bareJID, it).response {
//                    it.onSuccess {
//                        storeBundle(addr, it)
//                    }
//                }.send()
//            }
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
    fun retrieveDevicesIds(jid: BareJID, idFilter: String? = null): RequestBuilder<List<Int>, IQ> {
        log.fine {"fetching device ids for jid ${jid} on account ${context.boundJID?.bareJID}" }
        return pubsubModule.retrieveItem(jid = jid, node = DEVICE_LIST_NODE, itemId = idFilter, maxItems = if (idFilter != null) { null } else { 1 }).map { resp ->
            val value = resp.items.map { item ->
                item.content.toDeviceList()
            }.flatten()
            log.fine {"fetched device ids ${value} for jid ${jid} on account ${context.boundJID?.bareJID}" }
            return@map value;
        }
    }

    /**
     * Prepare request to retrieve public key bundle of given JabberID.
     * @param jid JabberID
     * @param bundleId requested bundle identifier.
     */
    fun retrieveBundle(jid: BareJID, bundleId: Int): RequestBuilder<Bundle, IQ> {
        return pubsubModule.retrieveItem(jid, "$BUNDLES_NODE_PREFIX$bundleId", maxItems = 1).map {
            it.items.firstOrNull()?.content?.toBundleOf(jid, bundleId) ?: throw XMPPException(
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
    fun publishDeviceList(deviceIds: List<Int>): PublishRequestBuilder<PubSubModule.PublishingInfo> {
        log.fine { "publishing device list: $deviceIds for account ${context.boundJID?.bareJID}" }
        val payload = element("list") {
            xmlns = XMLNS
            deviceIds.forEach { devId ->
                "device" {
                    attributes["id"] = devId.toString()
                }
            }
        }
        return publish(
            jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound."),
            node = DEVICE_LIST_NODE,
            itemId = CURRENT,
            payload = payload,
            publishOptions = PUBSUB_CONFIG_OPEN_ACCESS_MODEL
        )
    }

    /**
     * Prepare request to publish currently used own key bundle.
     * @param signedPreKeyId id of signed pre-key to publish.
     * @param preKeys list of pre-keys identifiers to publish.
     */
    fun publishOwnBundle(signedPreKeyId: Int, preKeys: List<Int>): PublishRequestBuilder<PubSubModule.PublishingInfo> {
        return publishBundle(
            protocolStore.getIdentityKeyPair(),
            protocolStore.getLocalRegistrationId(),
            protocolStore.loadSignedPreKey(signedPreKeyId),
            preKeys.map { protocolStore.loadPreKey(it) }
        )
    }

    fun startSession(jid: BareJID, deviceId: Int, handler: (Result<Unit>) -> Unit) {
        log.finest("retrieving bundle for " + jid.toString() + ", device id: ${deviceId}...")
        retrieveBundle(jid, deviceId).response {
            it.onSuccess {
                storeBundle(SignalProtocolAddress(jid.toString(), deviceId), it);
                handler(createSession(protocolStore, it));
            }
            it.onFailure {
                devicesFetchError[jid] = (devicesFetchError[jid] ?: emptyList()) + listOf(deviceId);
                log.warning(it, {"failed starting session for $jid, device id: $deviceId" })
                handler(Result.failure(it));
            }
        }.send()
    }

//    /**
//     * Starts OMEMO session with given JabberID.
//     * @param jid JabberID with which the session will be created.
//     * @param handler session creation result handler.
//     */
//    fun startSession(jid: BareJID, handler: (Result<OMEMOSession>) -> Unit) {
//        log.finest("retrieving bundles for " + jid.toString() + "...")
//        retrieveBundles(listOf(jid, context.boundJID!!.bareJID)) {
//            val session = startSession(jid, it)
//            if (session != null) handler.invoke(Result.success(session))
//            else handler.invoke(Result.failure(HalcyonException("Cannot create OMEMO session")))
//        }
//    }
//
//    /**
//     * Starts OMEMO session with given JabberID.
//     * @param jid JabberID with which the session will be created.
//     * @param bundles list of previously retrieved key bundles of given JabberID.
//     * @return created session or `null`.
//     */
//    fun startSession(jid: BareJID, bundles: List<Bundle>): OMEMOSession? {
//        try {
//            log.finest("starting session for jid " + jid.toString() + " with " + bundles.size + "...")
//            val myJid = context.boundJID!!.bareJID
//            val ciphers = createCiphers(protocolStore, bundles)
//            val session = OMEMOSession(protocolStore.getLocalRegistrationId(), myJid, jid, ciphers.toMutableMap())
//            sessionStore.storeOMEMOSession(session)
//            return session
//        } catch (e: Exception) {
//            log.warning("got exception while starting session: " + e.message)
//            return null
//        }
//    }

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
    ): PublishRequestBuilder<PubSubModule.PublishingInfo> {
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
        return publish(
            jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound."),
            node = "$BUNDLES_NODE_PREFIX$registrationId",
            itemId = CURRENT,
            payload = payload,
            publishOptions = PUBSUB_CONFIG_OPEN_ACCESS_MODEL
        )
    }

    private fun publish(jid: BareJID, node: String, itemId: String?, payload: Element?, publishOptions: JabberDataForm): PublishRequestBuilder<PubSubModule.PublishingInfo> {
        return PublishRequestBuilder<PubSubModule.PublishingInfo>(
            pubsubModule.publish(jid = jid, node = node, itemId = itemId, payload = payload, publishOptions = publishOptions)
        ).errorHandler { e, callback ->
            when {
                e is XMPPError && e.error == ErrorCondition.Conflict -> {
                    adjustNodeConfig(jid, node, publishOptions).response {
                        it.onFailure {
                            callback(Result.failure(it))
                        }
                        it.onSuccess {
                            pubsubModule.publish(
                                jid = jid,
                                node = node,
                                itemId = itemId,
                                payload = payload,
                                publishOptions = publishOptions
                            ).response { callback(it) }.send()
                        }
                    }.send()
                }
                else -> callback(Result.failure(e))
            }
        }
    }

    fun correctNodeConfigIfRequired() {
        val deviceId = protocolStore.getLocalRegistrationId();
        if (deviceId == 0) {
            return;
        }
        context.boundJID?.bareJID?.let { jid ->
            correctNodeConfigIfRequired(jid, DEVICE_LIST_NODE);
            correctNodeConfigIfRequired(jid, BUNDLES_NODE_PREFIX + deviceId);
        }
    }

    fun correctNodeConfigIfRequired(jid: BareJID, node: String) {
        pubsubModule.retrieveNodeConfig(pubSubJID = jid, node = node).response {
            it.onSuccess {
                if (it.getFieldByVar("pubsub#access_model")?.fieldValue == "open") {
                    // ok, nothing to do..
                } else {
                    adjustNodeConfig(jid, node, PUBSUB_CONFIG_OPEN_ACCESS_MODEL).response {
                        log.fine { "adjusted node configuration for $jid, node $node, result: $it" }
                    }.send()
                }
            }
        }.send()
    }

    private fun adjustNodeConfig(jid: BareJID, node: String, requiredOptions: JabberDataForm): RequestBuilder<Unit,IQ> {
        val x = JabberDataForm.create(FormType.Form);
        requiredOptions.getAllFields().forEach {
            x.addField(Field.create(varName = it.fieldName!!, type = it.fieldType).apply {
                it.fieldValues.let { this.fieldValues = it }
            })
        }
        return pubsubModule.configureNode(pubSubJID = jid, node = node, config = x);
    }
    
    class PublishRequestBuilder<V>(val requestBuilder: RequestBuilder<V,IQ>) {

        private var handlers: MutableList<RHandler<V>> = mutableListOf()
        private var errorHandler: ((Throwable,(Result<V>)->Unit)->Unit)? = null;

        fun response(handler: RHandler<V>): PublishRequestBuilder<V> {
            handlers.add(handler)
            return this;
        }

        fun errorHandler(errorHandler: (Throwable,(Result<V>)->Unit)->Unit): PublishRequestBuilder<V> {
            this.errorHandler = errorHandler;
            return this;
        }

        fun send() {
            requestBuilder.response { result ->
                result.onSuccess {
                    callHandlers(result);
                }
                if (errorHandler != null) {
                    result.onFailure {
                        errorHandler!!.invoke(it) {
                            callHandlers(it);
                        }
//                        errorHandler!!.invoke(it).onSuccess {
//                            it.response { callHandlers(it) }.send();
//                        }.onFailure {
//                            callHandlers(Result.failure(it));
//                        }
                    }
                } else {
                    result.onFailure {
                        for (handler in handlers) {
                            callHandlers(result);
                        }
                    }
                }
            }.send()
        }

        private fun callHandlers(result: Result<V>) {
            for (handler in handlers) {
                handler(result);
            }
        }
    }

    private fun afterReceive(element: Element?, chain: StanzaFilterChain) {
        if (element?.name != Message.NAME || element is OMEMOMessage) {
            chain.doFilter(element)
            return
        }

        val mamResult = element.getChildrenNS("result", "urn:xmpp:mam:2");
        if (mamResult != null) {
            mamResult.getChildren("forwarded").forEach { forwardedEl ->
                val messageEl = forwardedEl.getFirstChild("message");
                if (messageEl != null) {
                    forwardedEl.remove(messageEl);
                    forwardedEl.add(decodeMessage(messageEl));
                }
            }
            chain.doFilter(element);
        } else {
            chain.doFilter(decodeMessage(element));
        }
    }

    private fun decodeMessage(element: Element): Element {
        val senderJid = element.getFromAttr()
        val encElement = element.getChildrenNS("encrypted", XMLNS)
        val senderId =
            encElement?.getFirstChild("header")?.attributes?.get("sid")
                ?.toInt()

        if (senderJid == null || encElement == null || senderId == null) {
            return element;
        }

        val localJid = context.boundJID!!.bareJID;
        val session = OMEMOSession(
            protocolStore.getLocalRegistrationId(),
            localJid,
            emptyMap<SignalProtocolAddress, SessionCipher>().toMutableMap()
        )
        val result = OMEMOEncryptor.decrypt(protocolStore, session, wrap(element))
        if (result.second) {
            publishBundleIfNeeded();
        }
        return result.first;
    }

    private var bundleRefreshInProgress = false;

    fun publishBundleIfNeeded() {
        val registrationId = protocolStore.getLocalRegistrationId();
        if (registrationId == 0 || bundleRefreshInProgress) {
            log.fine { "skipping checking of OMEMO bundle publication for account ${context.boundJID?.bareJID}, local device id = ${registrationId}, refresh in progress = ${bundleRefreshInProgress}" }
            return;
        }

        context.boundJID?.bareJID?.let { jid ->
            bundleRefreshInProgress = true;
            pubsubModule.retrieveItem(jid, "$BUNDLES_NODE_PREFIX$registrationId", maxItems = 1).response {
                it.onSuccess {
                    protocolStore.loadSignedPreKeys()?.mapNotNull { it?.getId() }?.max()?.let { signedPreKeyId ->
                        val currentKeys =
                            it.items.firstOrNull()?.content?.getFirstChild("prekeys")?.children?.mapNotNull { it.attributes["preKeyId"]?.toIntOrNull() }
                                ?: emptyList();
                        
                        val validKeys = currentKeys.filter { protocolStore.containsPreKey(it) };
                        val needKeys = 150 - validKeys.size;
                        log.fine {"publishing new OMEMO bundle with ${needKeys} new keys for account ${jid}" }
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
                                    log.info { "publication of new OMEMO bundle succeeded for account ${jid}" }
                                    this.addDevice(registrationId)
                                    bundleRefreshInProgress = false;
                                }
                                it.onFailure { ex ->
                                    log.warning(ex, { "publication of new OMEMO bundle failed for account ${jid}, reverting..." })
                                    newKeys.forEach {
                                        protocolStore.removePreKey(it);
                                    }
                                    bundleStateStorage.updatePreKeyMax(nextPreKeyId - 1)
                                    bundleRefreshInProgress = false;
                                }
                                bundleRefreshInProgress = false;
                            }.send();
                        } else {
                            addDevice(registrationId);
                            bundleRefreshInProgress = false
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
                                       this.addDevice(registrationId)
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

//    private fun getOMEMOSession(jid: BareJID, senderId: Int, handler: (Result<OMEMOSession>) -> Unit) {
//        val s = sessionStore.getOMEMOSession(jid)
//        if (s != null) {
//            handler(Result.success(s))
//            return
//        }
//        startSession(jid) {
//            it.onSuccess { s ->
//                handler(Result.success(s))
//            }
//            it.onFailure { handler(Result.failure(HalcyonException("Cannot create session"))) }
//        }
//    }
//
//    private fun encryptAndSend(
//        element: Element,
//        chain: StanzaFilterChain,
//        recipient: FullJID,
//        session: OMEMOSession,
//        bodyEl: Element,
//        order: EncryptMessage
//    ) {
//        element.remove(bodyEl)
//
//        val encElement = OMEMOEncryptor.encrypt(session, bodyEl.value ?: "")
//
//        element.add(encElement)
//        element.add(element("store") {
//            xmlns = "urn:xmpp:hints"
//        })
//
//        element.clearControls()
//        chain.doFilter(element)
//    }
//
//    private fun createSessionEncryptAndSend(
//        element: Element,
//        chain: StanzaFilterChain,
//        recipient: FullJID,
//        bodyEl: Element,
//        order: EncryptMessage
//    ) {
//        log.finest("startig omemo session with " + recipient.bareJID.toString())
//        startSession(recipient.bareJID) {
//            print("awaiting omemo session creation....")
//            it.onSuccess {
//                log.finest("session created successfully!")
//                encryptAndSend(element, chain, recipient, it, bodyEl, order)
//            }
//            it.onFailure {
//                log.finest("session creation failed!!")
//                it.printStackTrace()
//            }
//        }
//    }

    private fun activeDevices(jid: BareJID): List<Int> = devices[jid]?.filterNot { devicesFetchError[jid]?.contains(it) ?: false } ?: emptyList();

    private fun ensureSessions(jid: BareJID, deviceIds: List<Int>, callback: (List<SignalProtocolAddress>)->Unit) {
        var addresses = deviceIds.map { SignalProtocolAddress(jid.toString(), it) }.toMutableList();
        val missingSessions = addresses.filterNot { protocolStore.containsSession(it) };
        log.fine { "got missing sessions ${missingSessions} for jid ${jid} at account ${context.boundJID?.bareJID}" }
        var counter = missingSessions.size;
        if (counter == 0) {
            callback(addresses);
            return;
        }
        
        val lock = Lock();
        missingSessions.forEach { addr ->
            log.fine { "starting session for ${addr} at account ${context.boundJID?.bareJID}" }
            startSession(jid, addr.getDeviceId()) {
                val isReady = lock.withLock {
                    if (it.isFailure) {
                        log.fine(it.exceptionOrNull(), { "failed to start session for ${addr} at account ${context.boundJID?.bareJID}" })
                        addresses.remove(addr);
                    } else {
                        log.fine { "started session for ${addr} at account ${context.boundJID?.bareJID}, remaining session counter: ${counter-1}" }
                    }
                    counter -= 1;
                    counter == 0
                }
                log.fine { "are we ready? isReady: ${isReady} for jid ${jid} at account ${context.boundJID?.bareJID}" }
                if (isReady) {
                    log.fine { "got active sessions for addresses ${addresses} for jid ${jid} at account ${context.boundJID?.bareJID}" }
                    callback(addresses);
                }
            }
        }
    }

    fun addresses(jids: List<BareJID>, callback: (List<SignalProtocolAddress>) -> Unit) {
        log.fine {"retrieving addresses for jids ${jids} on account ${context.boundJID?.bareJID}" }
        val lock = Lock();
        var allAddresses = mutableListOf<SignalProtocolAddress>()
        var counter = jids.size;
        val continuation = { addresses: List<SignalProtocolAddress> ->
            val isReady = lock.withLock {
                allAddresses.addAll(addresses);
                counter -= 1;
                counter == 0
            }
            if (isReady) {
                log.fine {"retrieved addresses for jids ${jids} on account ${context.boundJID?.bareJID}, result: ${allAddresses}" }
                callback(allAddresses);
            }
        }

        jids.forEach {
            addresses(it, continuation);
        }
    }

    fun addresses(jid: BareJID, callback: (List<SignalProtocolAddress>)->Unit) {
        log.fine { "retrieving addresses for jid $jid on account ${context.boundJID?.bareJID}" }
        val devices = activeDevices(jid);
        if (devices.isNotEmpty()) {
            log.fine { "got local info about devices for ${jid} on account ${context.boundJID?.bareJID}, result: ${devices}" }
            // encrypt for known active devices
            ensureSessions(jid, devices, callback);
        } else {
            // we need to discover active devices
            retrieveDevicesIds(jid).response {
                it.onSuccess {
                    log.fine { "got remote info about devices for ${jid} on account ${context.boundJID?.bareJID}, result: ${it}" }
                    this.devices[jid] = it
                    ensureSessions(jid, it, callback);
                }
                it.onFailure {
                    log.fine(it, { "failed to fetch remote info about devices for ${jid} on account ${context.boundJID?.bareJID}" })
                    callback(emptyList())
                }
            }.send()
        }
    }

    fun encryptAndSend(element: Element, chain: StanzaFilterChain, recipients: List<BareJID>, bodyEl: Element, order: EncryptMessage) {
        addresses(recipients) { addresses ->
            val localJid = context.boundJID!!.bareJID;
            log.fine("discovered remote addresses: ${addresses} for account ${localJid} for jid ${element.getToAttr()}")
            element.remove(bodyEl)

            val localAddresses = protocolStore.getSubDeviceSessions(localJid.toString()).map { SignalProtocolAddress(localJid.toString(), it) };
            log.fine("discovered local addresses: ${localAddresses} for account ${localJid}")
            val session = OMEMOSession(
                protocolStore.getLocalRegistrationId(),
                localJid,
                (localAddresses + addresses).distinct().associateBy({ k -> k }, { k -> SessionCipher(protocolStore, k) })
                    .toMutableMap()
            )

            val encElement = OMEMOEncryptor.encrypt(session, bodyEl.value ?: "")

            element.add(encElement)
            element.add(element("store") {
                xmlns = "urn:xmpp:hints"
            })

            element.add(element("body") {
                value = "[This message is OMEMO encrypted]"
            })

            element.clearControls()
            chain.doFilter(element)
        }
    }

    fun beforeSend(element: Element?, chain: StanzaFilterChain) {
        if (element?.name != Message.NAME) {
            chain.doFilter(element)
            return
        }

        val order = element.encryptionOrder()
        element.clearControls();
        val bodyEl = element.getFirstChild("body")
        val recipient = element.getToAttr();
        if (bodyEl != null && recipient != null) {
            when(order) {
                is EncryptMessage.No -> chain.doFilter(element)
                is EncryptMessage.Yes -> {
                    val recipients = order.jids.takeUnless { it.isEmpty() }?: listOf(recipient.bareJID)
                    encryptAndSend(element, chain, recipients, bodyEl, order)
                }
            }
        } else {
            chain.doFilter(element)
        }
    }

}

sealed interface EncryptMessage {
    data class Yes(val jids: List<BareJID> = emptyList()): EncryptMessage {}
    object No: EncryptMessage {}
}

private fun Element?.toDeviceList(): List<Int> =
    if (this?.name == "list" && this.xmlns == OMEMOModule.XMLNS) this.getChildren("device")
        .mapNotNull { it.attributes["id"]?.toInt() } else emptyList()

/**
 * Prepare request to add device to currently published device list.
 * @param deviceId device Id to add.
 */
fun OMEMOModule.addDevice(deviceId: Int) {
    val jid = context.boundJID?.bareJID ?: throw HalcyonException("JID not bound.")
    retrieveDevicesIds(jid).response {
        it.onSuccess { receivedList ->
            if (!receivedList.contains(deviceId)) {
                val newList = (receivedList + deviceId).distinct()
                publishDeviceList(newList).send()
            }
        }
        it.onFailure {
            if (it is XMPPError && it.error == ErrorCondition.ItemNotFound) {
                publishDeviceList(listOf(deviceId)).send()
            }
        }
    }.send()
}

/**
 * Adds encryption order to message.
 * @param value is `true` then message MUST be encrypted before send. if `false` then message will not be encrypted at all
 */
fun MessageNode.encrypt(value: EncryptMessage = EncryptMessage.Yes(emptyList())) {
    this.element.clearControls()
    element(OMEMOModule.ENCRYPTION_ELEMENT) {
        attributes["encryption"] = when (value) {
            is EncryptMessage.Yes -> "yes"
            is EncryptMessage.No -> "no"
        }
        when (value) {
            is EncryptMessage.Yes -> {
                for (jid in value.jids) {
                    element("jid") {
                        this.value = jid.toString()
                    }
                }
            }
            is EncryptMessage.No -> {}
        }
    }
}

private fun Element.encryptionOrder(): EncryptMessage {
    val e = this.getFirstChild(OMEMOModule.ENCRYPTION_ELEMENT) ?: return EncryptMessage.No
    return when (e.attributes["encryption"]) {
        "yes" -> EncryptMessage.Yes(e.getChildren("jid").mapNotNull { it.value?.toBareJID() })
        "no" -> EncryptMessage.No
        else -> throw HalcyonException("Invalid OMEMO control element.")
    }
}

private fun Element.clearControls() {
    this.getChildren(OMEMOModule.ENCRYPTION_ELEMENT).forEach {
        this.remove(it)
    }
}