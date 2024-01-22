StreamManagementModule
----------------------

The module implements `XEP-0198: Stream Management <https://xmpp.org/extensions/xep-0198.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install  Stream Management module, call function ``install`` inside Halcyon configuration (see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.sm.StreamManagementModule

    val halcyon = createHalcyon {
        install(StreamManagementModule)
    }

The module doesn't provides any configuration option.

Usage
^^^^^

Module is used internally by Halcyon library.

Events
^^^^^^

The module can fire event ``StreamManagementEvent`` which has three subclasses:

* ``Enabled`` fired when Stream Management is enabled successfully.

* ``Failed`` when Strem management cannot be enabled.

* ``Resumed`` when session is successfully resumed.