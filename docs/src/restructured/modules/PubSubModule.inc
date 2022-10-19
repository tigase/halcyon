RosterModule
-------------

This module implements `XEP-0060: Publish-Subscribe <https://xmpp.org/extensions/xep-0060.html>`__. It adds publish-subscribe functionality.

Events
^^^^^^^

PubSubEventReceivedEvent

Methods
^^^^^^^^

create(pubSubJID: JID, node: String, configForm: JabberDataForm? = null)

fun subscribe(pubSubJID: JID, node: String, jid: JID)

fun purgeItems(pubSubJID: JID, node: String)

fun retrieveSubscriptions(pubSubJID: JID, node: String)

fun modifySubscriptions(pubSubJID: JID, node: String, subscriptions: List<Subscription>)

fun deleteItem(jid: JID, node: String, itemId: String)

fun retrieveItem(jid: JID, node: String, itemId: String? = null)

fun publish(jid: JID?, node: String, itemId: String?, payload: Element? = null)

fun retrieveAffiliations(jid: JID?, node: String? = null)