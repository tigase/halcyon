DeliveryReceiptsModule
----------------------

The module is implementing `XEP-0184: Message Delivery Receipts <https://xmpp.org/extensions/xep-0184.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Message Delivery Receipts Module, call function ``install`` inside Halcyon configuration (see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.receipts.DeliveryReceiptsModule

    val halcyon = createHalcyon {
        install(DeliveryReceiptsModule) {
            autoSendReceived = true
            mode = DeliveryReceiptsModuleConfig.Mode.All
        }
    }

The module has two configuration options:

* The ``autoSendReceived`` causes automatic sending of ``received`` receipt to the sender, even before client
  processing the message.

* The ``mode`` configures automatic adding delivery receipt request to outgoing stanzas:

    * ``All`` - add request to all outgoing messages.

    * ``Off`` - automatic requests adding is turned off.

Usage
^^^^^

The module does not provides any functions. There is only set of extensions functions:

* ``deliveryReceiptRequest()`` to use with stanza builder to add request to stanza.

* ``isDeliveryReceiptRequested()`` to check if received message stanza contains delivery receipt request.

* ``getReceiptReceivedID()`` returns identifier of the message to which the delivery receipt refers if the receipt is attached to received message.

Events
^^^^^^

There is only one event fired by module: ``MessageDeliveryReceiptEvent``. It is fired, when any delivery receipt will be received.
