MessageCarbonsModule
---------------------

This module implements `XEP-0280: Message Carbons <https://xmpp.org/extensions/xep-0280.html>`__.

Events
^^^^^^^^^^

``Sent``
~~~~~~~~~

Fired when client receives carbon of message sent by other entity using the same account. Event contains carboned message.


``Received``
~~~~~~~~~~~~~

Fired when client receives carbon of message received by other entity using the same account. Event contains carboned message.


Methods
^^^^^^^^^

``enable()``
~~~~~~~~~~~~~~~~

Method prepares request to enable carbon messages in current session.

``disable()``
~~~~~~~~~~~~~~~~

Method prepares request to disable carbon messages in current session.
