StreamFeaturesModule
--------------------

Stream features module. The module is integrated part of XMPP Core protocol.
Module keeps information about set of features provided by current XMPP stream.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

The module is pre-installed in Halcyon and is required for the library to function correctly.

THe module doesn't provides any configuration options.

Usage
^^^^^

The module offers read-only property ``streamFeatures`` to get whole ``<stream:features/>`` XML element.
Because syntax of specific features may be different, there is no general mechanisms to help interpreting such data.

There is also function ``getFeatureOrNull(name, xmlns)`` which returns feature element or ``null``.

Events
^^^^^^

When new stream is opened and client received stream features element from the server, module will fire
``StreamFeaturesEvent`` containing received features element.