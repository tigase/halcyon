MUCModule
---------

Installation
^^^^^^^^^^^^

To install MucModule module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.muc.MUCModule

    val halcyon = createHalcyon {
        install(MUCModule)
    }

The only one configuration property ``store`` allows to use own implementation of rooms store.

Store
^^^^^

Be default MUC Module keeps in memory all required room data required to work (``DefaultMUCStore``). If you need, your application may keep those information persistently. But remember, that MUC protocol forces clients to join to room after each connection to XMPP server.

Joining room
^^^^^^^^^^^^

To join room use ``join()``function, to prepare request:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.muc.MUCModule

    halcyon.getModule(MUCModule)
        .join("room@muc.server.com".toBareJID(), "Alice")
        .send()

Due to the nature of the MUC, all results of actions are published as events. When join request will be accepted by server, you will receive ``MucRoomEvents.YouJoined`` event with ``Room`` object.
Check events in sealed class ``MucRoomEvents`` for details.

Configuring room
^^^^^^^^^^^^^^^^

There are two functions to handle room configuration: ``retrieveRoomConfig(Room)`` and ``updateRoomConfig(Room, JabberDataForm)``. Room configuration is ``JabberDataForm``, and all fields are described in MUC protocol documentation.

Events
^^^^^^

There are two types of events in MUC module:

* ``MucEvents`` events unrelated to joined room. In this category we have only one event: ``MucEvents.InvitationReceived``, which is fired when client receives invitation to room.
* ``MucRoomEvents`` events related to known room from Store.