AlternativeConnectionMethodModule
---------------------------------

This module implements `XEP-0156: Discovering Alternative XMPP Connection Methods <https://xmpp.org/extensions/xep-0156.html>`__.

Module is used internally by ``WebSocketConnector`` and it is preinstalled in JS target.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.discoaltconn.*

    val halcyon = createHalcyon {
        install(AlternativeConnectionMethodModule)
    }

Module doesn't provides any configuration.

Retrieving data
^^^^^^^^^^^^^^^

Module provides one async method to look-up for alternative connection method list:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.discoaltconn.*

    halcyon.getModule(AlternativeConnectionMethodModule)
           .discovery("tigase.org") { list: List<HostLink> ->
           }

As result you will get list of connection URLs with relation type, or empty list in case of failure.