.. _header-EntityCapabilitiesModule:

EntityCapabilitiesModule
------------------------

This module implements `XEP-0115: XMPP Ping <https://xmpp.org/extensions/xep-0115.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled EntityCapabilities module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule

    val halcyon = createHalcyon {
        install(EntityCapabilitiesModule) {
            node = "http://mycompany.com/bestclientever"
            cache = MyCapsCacheImplementation()
            storeInvalid = false
        }
    }

The ``EntityCapabilitiesModule`` configuration is provided by interface ``EntityCapabilitiesModuleConfig``.

* The ``node`` is URI to identify your software. As default library uses ``https://tigase.org/halcyon`` URI.

* With ``cache`` propery you can use own implementation of capabilities cache store, for example JDBC based, to keep all received capabilities between your application restarts.

* The ``storeInvalid`` property allow to force storing received capabilities with invalid versification string. By default, it is set to ``false``.
For more information about the possible consequences of disabling the validation verification string, refer to the `Security Considerations <https://xmpp.org/extensions/xep-0115.html#security>`__ chapter.

Getting capabilities
^^^^^^^^^^^^^^^^^^^^

You can get entity capabilities based on the presence received.

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule

    val caps = halcyon.getModule(EntityCapabilitiesModule)
        .getCapabilities(presence)

The primary use of the module is to define a list of features of the client with whom communication is taking place.
After receiving presence from client we can determine features implemented by it:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule

    val caps = halcyon.getModule(EntityCapabilitiesModule)
        .getCapabilities(presence)

