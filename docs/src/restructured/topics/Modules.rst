.. _header-modules:

Modules
=======

Architecture of Halcyon library is based on plugins (called modules). Every feature like authentication, sending and receiving messages or contact list management is implemented as module.
Halcyon contains all modules in single package (at least for now), so no need to add more dependencies.

To install module you have to use ``install`` function:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

    val halcyon = createHalcyon {
        install(DiscoveryModule)
    }

Most of modules can be configured. Configuration may be passed in ``install`` block:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

    val halcyon = createHalcyon {
        install(DiscoveryModule) {
            clientName = "My Private Bot"
            clientCategory = "client"
            clientType = "bot"
        }
    }

By default, function ``createHalcyon()`` automatically add all modules. If you want to configure your own set of modules, you have to disable this feature and add required plugins by hand:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule

    val halcyon = createHalcyon(installAllModules = false) {
        install(DiscoveryModule)
        install(RosterModule)
        install(PresenceModule)
    }

.. note::

   Despite of the name, with ``install`` you can also configure preinstalled modules!

Halcyon modules mechanism is implementing modules dependencies, it means that if you install module (for example) ``MIXModule``, Halcyon automatically install modules ``RosterModule``, ``PubSubModule`` and ``MAMModule`` with default configuration.

There is also set of aliases, to make configuration of popular modules more comfortable.

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.builder.bind

    val halcyon = createHalcyon() {
        bind {
            resource = "my-little-bot"
        }
    }

In this example we used ``bind{}`` instead of ``install(BindModule){}``.

List of aliases:

+-----------------------+-----------------------------+
| Alias                 | Module name                 |
+=======================+=============================+
| ``bind()``            | ``BindModule``              |
+-----------------------+-----------------------------+
| ``sasl()``            | ``SASLModule``              |
+-----------------------+-----------------------------+
| ``sasl2()``           | ``SASL2Module``             |
+-----------------------+-----------------------------+
| ``discovery()``       | ``DiscoveryModule``         |
+-----------------------+-----------------------------+
| ``capabilities()``    | ``EntityCapabilitiesModule``|
+-----------------------+-----------------------------+
| ``presence()``        | ``PresenceModule``          |
+-----------------------+-----------------------------+
| ``roster()``          | ``RosterModule``            |
+-----------------------+-----------------------------+

.. include:: modules/SaslModule.rst
.. include:: modules/PresenceModule.rst
.. include:: modules/RosterModule.rst
.. include:: modules/DiscoveryModule.rst
.. include:: modules/EntityCapabilitiesModule.rst
.. include:: modules/PingModule.rst