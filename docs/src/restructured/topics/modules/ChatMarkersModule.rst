ChatMarkersModule
-----------------

The module is implementing `XEP-0333: Chat Markers <https://xmpp.org/extensions/xep-0333.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Chat Markers Module, call function ``install`` inside Halcyon configuration (see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkersModule

    val halcyon = createHalcyon {
        install(ChatMarkersModule) {
            autoSendReceived = true
            mode = ChatMarkersModuleConfig.Mode.Auto
        }
    }

The module has two configuration options:

* The ``autoSendReceived`` causes automatic sending of ``received`` marker to the sender, even before client
  processing the message.

* The ``mode`` configures automatic adding chat marker request to outgoing stanzas:

    * ``All`` - add request to all outgoing stanzas.

    * ``Auto`` - if message is send to full JID, then request will be added only if recipient supports Chat Markers.
      Support of Chat Markers is determined by Entity Capabilities.
      If message is send to bare JID, then request is adding always.

Usage
^^^^^

The module provides single method ``markMessage`` what prepares to send stanza with specific marker to recipient.

.. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.chatmarkers.ChatMarkersModule

    halcyon.getModule(ChatMarkersModule)
        .markMessage("alice@server.com".toJID(), "123", Marker.Displayed)
        .send()

There is also set of methods to work with Markers on received stanzas:

* ``isChatMarkerRequested()`` extends an ``Element`` so You can call it on every received stanza and check if sender
  added Chat Markers request to it.

* ``getChatMarkerOrNull()`` also extends an ``Element`` and returns object containing all information to correctly
  process received chat markers.

* ``markable()`` to use with stanza builder, to manually add chat marker request:

  .. code:: kotlin

    import tigase.halcyon.core.xmpp.modules.chatmarkers.*

    message {
        to = "alice@server.com".toJID()
        body = "Hello"
        markable()
    }

Events
^^^^^^

There is only one event fired by module: ``ChatMarkerEvent``. It is fired, when any Chat Marker will be received.

