SASLModule
-----------

Module is responsible for whole client authentication process.

Properties
^^^^^^^^^^^

-  ``saslContext`` contains context of module. Context is cleared when connection is started. It has several fields to read:

   -  ``mechanism`` - used SASL mechanism,

   -  ``state`` - current state of authentication process,

   -  ``complete`` - ``true`` if authentication process is finished (it doesn’t matter with success or with error).


Events
^^^^^^^

``SASLStarted``
~~~~~~~~~~~~~~~~~~~~

Fired when authentication process begins.

Fields:

-  ``mechanism`` - name of used SASL mechanism.

``SASLSuccess``
~~~~~~~~~~~~~~~~

Fired when authentication is successful.

``SASLError``
~~~~~~~~~~~~~~~

Fired when authentication finished with error.

Fields:

-  ``error`` - enum with type of error. SASL errors are described if `RFC <https://xmpp.org/rfcs/rfc6120.html#sasl-errors>`__.

-  ``description`` - human readable description of error (if provided by server).

Methods
^^^^^^^^^^^

There is no reason to call methods from this module in client. This module is used internally by Halcyon library.

``startAuth()``
~~~~~~~~~~~~~~~~~

This method begins authentication process. It doesn’t return Request object.
