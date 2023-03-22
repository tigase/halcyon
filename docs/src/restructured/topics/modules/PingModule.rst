PingModule
----------

This module implements `XEP-0199: XMPP Ping <https://xmpp.org/extensions/xep-0199.html>`__.

Install
^^^^^^^

To install or configure preinstalled Discovery module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.PingModule

    val halcyon = createHalcyon {
        install(PingModule)
    }

This module has no configuration options.

.. note::

   If module will not be installed, other entities will not be able to ping application.

Pinging entity
^^^^^^^^^^^^^^

Module provides function ``ping`` to prepare request for ping given entity:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.PingModule
    import tigase.halcyon.core.xmpp.toJID

    halcyon.getModule(PingModule)
        .ping("tigase.org".toJID())
        .response { result ->
            result.onSuccess { pong -> println("Pong: ${pong.time}ms") }
            result.onFailure { error -> println("Error $error") }
        }
        .send()

In the case of success, module returns `PingModule.Pong` class containing information about measured response time.