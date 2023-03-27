.. _header-PresenceModule:

PresenceModule
--------------

Module for handling received presence information.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Presence module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
    import tigase.halcyon.core.xmpp.modules.presence.InMemoryPresenceStore

    val halcyon = createHalcyon {
        install(PresenceModule) {
            store = InMemoryPresenceStore()
        }
    }

The only one configuration property ``store`` allows to use own implementation of presence store.

Setting own presence status
^^^^^^^^^^^^^^^^^^^^^^^^^^^

After connection is established, PresenceModule automatically sends initial presence.

To change own presence status, you should use `sendPresence` function.`

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.presence.PresenceModule

    halcyon.getModule(PresenceModule)
        .sendPresence(
            show = Show.Chat,
            status = "I'm ready for party!"
        )
        .send()

It is also possible to send direct presence, only for specific recipient:

 .. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.presence.PresenceModule

    halcyon.getModule(PresenceModule)
        .sendPresence(
            jid = "mom@server.com".toJID(),
            show = Show.DnD,
            status = "I'm doing my homework!"
        )
        .send()

Presence subscription
^^^^^^^^^^^^^^^^^^^^^

Details of managing presence subscription are explained in `XMPP specification <https://www.rfc-editor.org/rfc/rfc6121.html#section-3>`_.
Here we simply show how to subscribe and unsubscribe presence with Halcyon library.

All subscriptions manipulation may be done with single `sendSubscriptionSet` function:


 .. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.presence.PresenceModule

    halcyon.getModule(PresenceModule)
        .sendSubscriptionSet(jid = "buddy@somewhere.com".toJID(), presenceType = PresenceType.Subscribe)
        .send()

Depends on action you want, you have to change ``presenceType`` parameter:

Subscription request:
   Use ``PresenceType.Subscribe`` to send subscription request to given JabberID.

Accepting subscription request:
   Use ``PresenceType.Subscribed`` to accept  subscription request from given JabberID.

Rejecting subscription request:
   Use ``PresenceType.Unsubscribed`` to reject subscription request or cancelling existing subscription to our presence from given JabberID.

Unsubscribe contact:
   Use ``PresenceType.Unsubscribe`` to cancel your subscription of given JabberID presence.

.. note::

   Remember that subscription manipulation can affect your roster content.

Checking presence
^^^^^^^^^^^^^^^^^

When you develop application, probably you will want to check presence of your contact, to see if he is available.
Halcyon provides few function for that: ``getBestPresenceOf`` returns presence with highest priority (in case if there are few entities under the same bare JID);
``getPresenceOf`` returns last received presence of given full JID. You can also check list of all entities resources logged as single bare JID with ``getResources`` function.

Because determining of contact presence using low-level XMPP approach is not so intuitive, we introduced ``TypeAndShow``. It joins presence stanza type and show extension in single set of enums.

 .. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
    import tigase.halcyon.core.xmpp.modules.presence.typeAndShow

    val contactStatus = halcyon.getModule(PresenceModule)
        .getBestPresenceOf("dad@server.com".toBareJID())
        .typeAndShow()

Thanks to it, ``contactStatus`` value will contain easy to show contact status like online, offline, away, etc.

Events
^^^^^^

Module can fire two types of events:

* ``PresenceReceivedEvent`` is fired when any Presence stanza is received by client. Event contains JID of sender, stanza type (copied from stanza) and whole received stanza.

* ``ContactChangeStatusEvent`` is fired when received stanza changes contact presence (all subscriptions requests are ignored). Event contains JID of sender, human readable status description, current presence with highest priority and just received presence stanza. Note that ``presence`` in this event may contain stanza received long time ago. Current event is caused by receiving presence from entity with lower priority.
