DiscoveryModule
----------------

This module implements `XEP-0030: Service Discovery <https://xmpp.org/extensions/xep-0030.html>`__.

Properties
^^^^^^^^^^^

There are few properties to set in this module:

-  ``clientName`` - Client name.

-  ``clientVersion`` - Version of client.

-  ``clientCategory`` - Category of client.

-  ``clientType`` - Type of client.

Category and type of client are described in `Service Discovery Identities <https://xmpp.org/registrar/disco-categories.html>`__ document.


Events
^^^^^^^

``ServerFeaturesReceivedEvent``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Fired when server features, to where client is connected, are received. Client asks for server features automatically during login process.

``AccountFeaturesReceivedEvent``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Fired when user account features are received. Client asks for those features automatically.

Methods
^^^^^^^

``info(JID, String)``
~~~~~~~~~~~~~~~~~~~~~

Method prepares ``disco#info`` request. As parameters it takes JID of entity and node name. Both are optional. In response returns object ``Info`` contains JID of entity, node name, list of identities and list of features.

``items(JID, String)``
~~~~~~~~~~~~~~~~~~~~~~~

Method prepares ``disco#items`` request. As parameters it takes JID of entity and node name. Both are optional. In response returns object ``Items`` contains JID of entity, node name and list of items.

``findComponent((Info) → Boolean, (Info) → Unit)``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This method may be used to find component on currently connected server with specific features or type.

As first parameter it takes condition checker, which much check if given ``Info`` is this object what we are searching for. Second parameter is consumer.

.. code:: kotlin

   findComponent({ candidate ->
                   candidate.identities.any { it.type == "mix" }
               }) { result ->
       println("${result.jid}")
   }
