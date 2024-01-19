MessageModule
-------------

Message Stanza Handler. The module is integrated part of XMPP Core protocol.


Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Message module, call function ``install`` inside Halcyon configuration (see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.MessageModule

    val halcyon = createHalcyon {
        install(MessageModule)
    }

The module doesn't provides any configuration option.

Usage
^^^^^

The module does not provides any methods. Its only responsibility is fire event when Message stanza is received.
To create and send message stanza you should use stanza builder from Halcyon instance:

.. code:: kotlin

    val msg = halcyon.request.message {
        to = "alice@server.com".toJID()
        type = MessageType.Chat
        body = "Hello"
    }
    msg.send()

Events
^^^^^^

Event ``MessageReceivedEvent`` is fired when message stanza is received. It contains sender JabberID and stanza.
In some cases, when :ref:`MIXModule` or :ref:`PubSubModule` are installed, and received stanza is send by MIX or
PubSub component, event will not be fired, because responsibility for the incoming stanza is moved to specific
Halcyon module.

