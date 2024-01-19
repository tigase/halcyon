ChatStateModule
---------------

This module implements `XEP-0085: Chat State Notifications <https://xmpp.org/extensions/xep-0085.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Chat State module, call function ``install`` inside Halcyon configuration (see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.chatstates.ChatStateModule

    val halcyon = createHalcyon {
        install(ChatStateModule)
    }

The module doesn't supports any additional configuration.

Usage
^^^^^

The only method in module is ``sendChatState(jid, state)`` method, to prepare stanza with given chat state.

There is also `Message`` stanza extension property ``chatState`` to get and set from message object:

.. code:: kotlin

    val msg: Message = ...
    val chatState: ChatState? = msg.chatState

Events
^^^^^^

When library receives stanza with someones Chat State update, it emmit event ``ChatStateEvent`` containing sender
JabberID and chat state.

Chat State Machine
^^^^^^^^^^^^^^^^^^

To make Chat States handling more comfortable, we prepared ``ChatStateMachine``. It is tool to help calculate current
Chat State of our chat. Each chat screen (chat window) in client should have separate instance of ``ChatStateMachine``.
Class provides few methods to current state of chat:

* ``focused()`` should be called, when user activate window.

* ``focusLost()`` should be called, when user deactivate window.

* ``closeChat()`` should be called, when chat window is closed.

* ``composing()`` should be called, when user is typing. Method may be called on every key press.

* ``sendingMessage()`` should be called, when user press Send button.

Tool will calculate current state of chat and it fires ``OwnChatStateChangeEvent`` containing JabberID of recipient
notification, previous and current chat state. This event is received by ``ChatStateModule`` which automatically
emits new state.

To make state machine works correctly, we need to periodically call method ``update()``. Thanks to it, state machine
will be able to calculate ``Inactive`` state itself.

.. code:: kotlin

    val csm = ChatStateMachine(
        jid = "alice@server.com".toBareJID(),
        eventBus = halcyon.eventBus,
        sendUpdatesAutomatically = true
    )

    halcyon.eventBus.register(TickEvent) { csm.update() }

    csm.focused()

In above example, ``ChatStateMachine`` will send updates to ``alice@server.com`` itself, because
``sendUpdatesAutomatically`` is ``true``. If you want to send chat state updates in your code, just turn off
``sendUpdatesAutomatically``.

The easiest way to update state machine periodically is using ``TickEvent``. It is fired by Halcyon every 2 seconds.

Don't forget to unregister ``TickEvent`` handler when you close chat and ``ChatStateMachine`` will not be used anymore.