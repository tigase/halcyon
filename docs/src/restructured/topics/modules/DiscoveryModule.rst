DiscoveryModule
---------------

This module implements `XEP-0030: Service Discovery <https://xmpp.org/extensions/xep-0030.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Discovery module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

    val halcyon = createHalcyon {
        install(DiscoveryModule) {
            clientCategory = "client"
            clientType = "console"
            clientName = "Code Snippet Demo"
            clientVersion = "1.2.3"
        }
    }

The ``DiscoveryModule`` configuration is provided by interface ``DiscoveryModuleConfiguration``.

* The ``clientCategory`` and ``clientType`` properties provides information about category and type of client you develop.
List of allowed values you can use is published in `Service Discovery Identities <https://xmpp.org/registrar/disco-categories.html>`__ document.

* The ``clientName`` and ``clientVersion`` properties contains human readable software name and version.

.. note::

   If you change client name and version, it is good to update ``node`` name in :ref:`header-EntityCapabilitiesModule`.

Discovering information
^^^^^^^^^^^^^^^^^^^^^^^

Module provides function ``info`` to prepare request to get information about given entity:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
    import tigase.halcyon.core.xmpp.toJID

    halcyon.getModule(DiscoveryModule)
        .info("tigase.org".toJID())
        .response { result ->
            result.onFailure { error -> println("Error $error") }
            result.onSuccess { info ->
                println("Received info from ${info.jid}:")
                println("Features " + info.features)
                println(info.identities.joinToString { identity ->
                    "${identity.name} (${identity.category}, ${identity.type})"
                })
            }
        }
        .send()

In case of success, module return ``DiscoveryModule.Info`` class containing information about requested JID and node, list of received identities and list of features.

Discovering list
^^^^^^^^^^^^^^^^


Second feature provided by module is discovering list of items associated with an entity. It is implemented by the ``items`` function:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
    import tigase.halcyon.core.xmpp.toJID

    halcyon.getModule(DiscoveryModule)
        .items("tigase.org".toJID())
        .response { result ->
            result.onFailure { error -> println("Error $error") }
            result.onSuccess { items ->
                println("Received info from ${items.jid}:")
                println(items.items.joinToString { "${it.name} (${it.jid}, ${it.node})" })
            }
        }
        .send()

In case of success, module return ``DiscoveryModule.Items`` class containing information about requested JID, node and list of received items.

Events
^^^^^^

After connection to server is established, module automatically requests for for features of user account and server.

When Halcyon receives account information, then ``AccountFeaturesReceivedEvent`` event is fired. In case of receiving XMPP server information, Halcyon fires ``ServerFeaturesReceivedEvent`` event.