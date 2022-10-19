PingModule
------------

This module implements `XEP-0199: XMPP Ping <https://xmpp.org/extensions/xep-0199.html>`__. It allows to ping XMPP entities over XML stream.

Published features
^^^^^^^^^^^^^^^^^^^

-  ``urn:xmpp:ping``

Methods
^^^^^^^^^^

``ping(JID)``
~~~~~~~~~~~~~~

This method prepares ping request. Response object ``Pong`` contains measured round-trip time in miliseconds.
