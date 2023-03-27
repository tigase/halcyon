.. _header-RosterModule:

RosterModule
------------

Module for managing roster (contact list).

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Roster module, call function ``install`` inside Halcyon configuration (see :ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.roster.RosterModule
    import tigase.halcyon.core.xmpp.modules.roster.InMemoryRosterStore

    val halcyon = createHalcyon {
        install(RosterModule) {
            store = InMemoryRosterStore()
        }
    }

The only one configuration property ``store`` allows to use own implementation of roster store.

Retrieving roster
^^^^^^^^^^^^^^^^^

When connection is established, client automatically requests for latest roster, so no additional actions are required.

Most modern XMPP server supports roster versioning. Thanks to it, client do not have to receive whole roster from server
(which can be large). So we recommend, to implement own RosterStore to keep current roster content between client launches.

Manipulating roster
^^^^^^^^^^^^^^^^^^^

To add new contact to you roster you have to call ``addItem`` function:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.roster.RosterModule
    import tigase.halcyon.core.xmpp.modules.roster.RosterItem

    halcyon.getModule(RosterModule)
        .addItem(
            RosterItem(
                jid = "contact@somewhere.com".toBareJID(),
                name = "My friend",
            )
        )
        .send()

.. warning::

   Remember, that (as described in RFC) after call (and send) roster modification request, your local store will not be updated immediately.
   Roster store is updated only on server request!

When roster item is saved in your roster store, Halcyon fires ``RosterEvent.ItemAdded`` event.

To modify existing roster item, you have to call exactly the same ``addItem`` function:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.roster.RosterModule
    import tigase.halcyon.core.xmpp.modules.roster.RosterItem

    halcyon.getModule(RosterModule)
        .addItem(
            RosterItem(
                jid = "contact@somewhere.com".toBareJID(),
                name = "My best friend!",
            )
        )
        .send()

The difference is that after local store update Halcyon fires ``RosterEvent.ItemUpdated`` event.

Last thing is removing items from roster:

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.roster.RosterModule
    import tigase.halcyon.core.xmpp.modules.roster.RosterItem

    halcyon.getModule(RosterModule)
        .deleteItem("contact@somewhere.com".toBareJID())
        .send()

When item will be removed from local store, Halcyon fires ``RosterEvent.ItemRemoved`` event.

Events
^^^^^^

Roster module can fires few types of events:

* ``RosterEvent`` is fired when roster item in your local store is modified by server request. There are three sub-events: ``ItemAdded``, ``ItemUpdated`` and ``ItemRemoved``.
* ``RosterLoadedEvent`` inform us that roster data loading is finished. It is called only after retrieving roster on client request.
* ``RosterUpdatedEvent`` is fired, when processing roster data from server is finished. I will be triggered after requesting roster from server and after processing set of roster item manipulations initiated by server.