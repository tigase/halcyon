BlockingCommandModule
-----------------------

This module implements `XEP-0191: Blocking Command <https://xmpp.org/extensions/xep-0191.html>`__ and `XEP-0377: Spam Reporting <https://xmpp.org/extensions/xep-0377.html>`__

Events
^^^^^^^

``Blocked``
~~~~~~~~~~~~~~

Event fired when new contact was blocked. It contains few properties:

-  ``jid`` - blocked JabberID

-  ``reason`` - reason of blocking.

-  ``text`` - optional human-readable description of blocking reason.


``Unblocked``
~~~~~~~~~~~~~~

Event fired, when contact was unblocked. It contains one property:

-  ``jid`` - unblocked JabberID


``UnblockedAll``
~~~~~~~~~~~~~~~~~~

Event fired when all contacts are unblocked.

.. code:: kotlin

   halcyon.eventBus.register<BlockingCommandEvent>(BlockingCommandEvent.TYPE) { event ->
       when (event) {
           is BlockingCommandEvent.Blocked -> println("$event")
           is BlockingCommandEvent.Unblocked -> println("$event")
           is BlockingCommandEvent.UnblockedAll -> println("All blocked contacts are unblocked now!")
       }
   }


Methods
^^^^^^^^

Samples:

``retrieveList(JID, String)``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Retrieves list of blocked contacts. It allows to get list of blocked list, without information about reason.

.. code:: kotlin

   halcyon.getModule<BlockingCommandModule>(BlockingCommandModule.TYPE).retrieveList().response {
       it.onSuccess {
           println("Blocked: $it")
       }
       it.onFailure { println("Oops!") }
   }.send()


``block``
~~~~~~~~~~~~~~

Blocks given contact.

Params:

-  ``jid`` - BareJID of contact to block

-  ``reason`` - reason of blocking (optional, default value is ``NotSpecified``)

-  ``text`` - optional, human-readable description of blocking

.. code:: kotlin

   halcyon.getModule<BlockingCommandModule>(BlockingCommandModule.TYPE)
       .block("spammer@server.com".toBareJID(), Reason.Spam, "It is SPAMMER!!!")
       .response {
           it.onSuccess { println("Done") }
           it.onFailure { println("Oops!") }
       }.send()

``unblock``
~~~~~~~~~~~~~~

Unblocks given contact.

Params:

-  ``jids`` - BareJIDs of contacts to unblock

.. note::

   If ``jids`` will be empty, all blocked contacts will be unblocked!

.. code:: kotlin

   halcyon.getModule<BlockingCommandModule>(BlockingCommandModule.TYPE)
       .unblock("spammer@server.com".toBareJID())
       .response {
           it.onSuccess { println("Done") }
           it.onFailure { println("Oops!") }
       }.send()

``unblockAll``
~~~~~~~~~~~~~~

Unblocks all blocked contacts.

.. code:: kotlin

   halcyon.getModule<BlockingCommandModule>(BlockingCommandModule.TYPE)
       .unblockAll().response {
           it.onSuccess { println("Done") }
           it.onFailure { println("Oops!") }
       }.send()
