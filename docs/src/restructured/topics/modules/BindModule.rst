BindModule
----------

The module is integrated part of XMPP Core protocol. It is required by library and it is always preinstalled.

Configure
^^^^^^^^^

The module provides only one configuration option:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.*

    val halcyon = createHalcyon {
        bind {
            resource = "MyLaptop"
        }
    }

The ``resource`` property is suggested name of resource. Server may overwrite it. Server also will generate resource
name itself if it will be not provided in client.

Binded JID
^^^^^^^^^^

The module provides a way to get full JabberID of client after binding:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.*

    val myJID: FullJID = halcyon.getModule(BindModule).boundJID

But a better way to check the current JID the client is using is to check the Context:

.. code:: kotlin

    val myJID: FullJID = halcyon.boundJID

Events
^^^^^^

The module can fire two types of ``BindEvent``: ``Success`` with bound JabberID or ``Failure`` with error description.

.. code:: kotlin

    halcyon.eventBus.register(BindEvent){
        when(it){
            is BindEvent.Success -> println(it.jid)
            is BindEvent.Failure -> println("oops")
        }
    }
