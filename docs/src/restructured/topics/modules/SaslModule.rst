.. _header-SaslModule:

SaslModule & Sasl2Module
------------------------

``SaslModule`` and ``Sasl2Module`` provides mechanism to authenticate user in XMPP server.
Our current implementation supports a set of mechanisms to do that:

- ``SCRAM-SHA-1`` & ``SCRAM-SHA-1-PLUS``
- ``SCRAM-SHA-256`` & ``SCRAM-SHA-256-PLUS``
- ``SCRAM-SHA-512`` & ``SCRAM-SHA-512-PLUS``
- ``PLAIN``

All ``SCRAM`` mechanisms with ``PLUS`` allow to bind authentication process with TLS channel. It makes authentication
process more secure and protect against man-in-the-middle attack.

SCRAM mechanisms in Halcyon supports three types on channel binding: ``tls-unique``, ``tls-exporter`` and ``tls-server-end-point``.
Unfortunately, because of limitation of Java TLS API implementation, by default only ``tls-server-end-point`` is
supported.

To enable other channel binding types, you have to use BouncyCastle based TLS processor. It is provided by separate
module, so you need to import it to your project

.. code:: kotlin

    implementation("tigase.halcyon:halcyon-bouncycastle:$HalcyonVersion")

and configure socket connector:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.builder.socketConnector
    import tigase.halcyon.core.connector.socket.BouncyCastleTLSProcessor

    val halcyon = createHalcyon {
        socketConnector {
            tlsProcessorFactory = BouncyCastleTLSProcessor
       }
    }

SaslModule vs Sasl2Module
^^^^^^^^^^^^^^^^^^^^^^^^^

``Sasl2Module`` does exactly the same what ``SaslModule``. The only difference is that ``Sasl2Module`` is used with
`Bind 2 <https://xmpp.org/extensions/xep-0386.html>`__ mechanism.

Events
^^^^^^

``SASLEvent`` has three subtypes of events:

* ``SASLStarted`` fired when authentication process begins, it also provides used mechanism;
* ``SASLSuccess`` fired when authentication process is finished with success;
* ``SASLError`` fired on authentication error, it provides sasl error type and optional human readable description.



.. _BouncyCastle: https://www.bouncycastle.org/java.html