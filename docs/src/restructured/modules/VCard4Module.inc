VCardModule
-----------

This module allows to publishing and retrieving VCard4 as described in `XEP-0292 <https://xmpp.org/extensions/xep-0292.html>`__.

Properties
^^^^^^^^^^^

-  ``autoRetrieve`` - If ``true`` then module automatically retrieve VCard before firing ``VCardUpdatedEvent``. It is ``false`` by default.

Events
^^^^^^^

``VCardUpdatedEvent``
~~~~~~~~~~~~~~~~~~~~~~~~~

Fired when VCard update is received from PEP. Contains JID that the update applies to. If ``autoRetrieve`` is set to ``true`` then event will contains current VCard.

Methods
^^^^^^^^^

``retrieveVCard(jid: BareJID)``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This method prepare request for retrieving VCard of given JID. As result returns ``VCard`` object.


``fun publish(vcard: VCard)``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This method prepare request for publishing own vcard.


Usage example
^^^^^^^^^^^^^^^

Retrieving VCard
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code:: kotlin

   val vCardModule = halcyon.getModule<VCardModule>(VCardModule.TYPE)!!
   vCardModule.retrieveVCard("someone@server.im".toBareJID()).response { result ->
       result.onSuccess {vcard->
           println("""
               Received vcard:
               Name: ${vcard.formattedName}
               Name: ${vcard.structuredName?.given} ${vcard.structuredName?.surname}
               Nick: ${vcard.nickname}
               Birthday: ${vcard.birthday}
               TimeZone: ${vcard.timeZone}
           """.trimIndent())

           println()
           vcard.addresses.forEach {addr->
               println("""
                   ${addr.street}
                   ${addr.locality} ${addr.region} ${addr.code}
                   ${addr.country}

               """.trimIndent())
           }

       }
       result.onFailure {
           println("Cannot retrieve VCard. Error: $it")
       }
   }.send()


Publishing VCard
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Publishing own vcard is very simple:

.. code:: kotlin

   val vCardModule = halcyon.getModule<VCardModule>(VCardModule.TYPE)!!
   vCardModule.publish(vcard).response { result ->
       result.onSuccess { println("VCard published") }
       result.onFailure { println("VCard NOT published") }
   }.send()

VCard object is mutable and can be edited. To create new VCard instance you can use VCard builder:

.. code:: kotlin

   val vCard = vcard {
       structuredName {
           given = "Alice"
           surname = "Carl"
       }
       nickname = "alice"
       email {
           parameters {
               pref = 1
               +"work"
           }
           text = "alice@organisation.com"
       }
   }
