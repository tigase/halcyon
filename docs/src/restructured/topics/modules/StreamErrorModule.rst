StreamErrorModule
-----------------

Stream Error Handler. The module is integrated part of XMPP Core protocol.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

The module is pre-installed in Halcyon and is required for the library to function correctly.

THe module doesn't provides any configuration options.

Events
^^^^^^

In case of receiving stream error, module fires event ``StreamErrorEvent`` containing original received XML element,
parsed stream error type and error children element.