Creating and configuring a client
=================================

When repositories and dependencies are configured, we can create instance of Halcyon:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon

    val halcyon = createHalcyon {
    }

Authentication
--------------

Of course, it requires a bit of configuration: to connect to XMPP Server, client requires username and password:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.toBareJID

    val halcyon = createHalcyon {
        auth {
            userJID = "username@xmppserver.com".toBareJID()
            password { "secretpassword" }
        }
    }

Registering new account
-----------------------

To register new account on XMPP server you need separate instance of Halcyon, configured exactly for this purpose.

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon

    val halcyon = createHalcyon {
        register {
            domain = "xmppserver.com"
            registrationFormHandler { form ->
                form.getFieldByVar("username")!!.fieldValue = "username"
                form.getFieldByVar("password")!!.fieldValue = "password"
            }
        }
    }

.. note::

   The server may provide a different set of fields and it is the developer's responsibility to handle them.

Connectors
----------

Halcyon library is able to use many connection methods, depends on platform. By default JVM and Android uses Socket and JavaScript uses WebSocket connector.

JVM SocketConnector
~~~~~~~~~~~~~~~~~~~

In Socket Connector you may configure own DNS resolver, set custom host and port, and define trust manager to check SSL server certificates.

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.builder.socketConnector

    val halcyon = createHalcyon {
        socketConnector {
            dnsResolver = CustomDNSResolver()
            hostname = "127.0.0.1"
            port = 15222
            trustManager = MyTrustManager()
        }
    }

.. warning::

   Note that by default Halcyon doesn't check SSL server certificates at all!

Halcyon provides two TLS processors: default one, using built-in JSSE and the second using BouncyCastle_.
The only reason and difference between them is fact that JSSE_ doesn't provides a way to get Channel Binding data to
use in SASL SCRAM (see :ref:`header-SaslModule`) authentication protocol.

If you want to use BouncyCastle, you have to import ``tigase.halcyon:halcyon-bouncycastle`` to your project, and
add ``BouncyCastleTLSProcessor`` to configuration of connector:

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.builder.socketConnector
    import tigase.halcyon.core.connector.socket.BouncyCastleTLSProcessor

    val halcyon = createHalcyon {
        socketConnector {
            dnsResolver = CustomDNSResolver()
            hostname = "127.0.0.1"
            port = 15222
            trustManager = MyTrustManager()
            tlsProcessorFactory = BouncyCastleTLSProcessor
       }
    }

JavaScript WebSocketConnector
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If your target platform is JavaScript, then default connector will use WebSocket.

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.builder.webSocketConnector

    val halcyon = createHalcyon {
        webSocketConnector {
            webSocketUrl = "ws://127.0.0.1:5290/"
        }
    }

WebSocket connector has only one configuration parameter: server URL.


Starting and stopping
---------------------

Now we are ready to connect client to the XMPP server:

.. code:: kotlin

    halcyon.connectAndWait()
    halcyon.disconnect()

Method ``connectAndWait()`` is JVM only method, it esteblish connection in blocking way. To start connection in async mode you have to use ``connect()`` method.
If library was configured to register new account, thise method will start registration process.
Method ``disconnect()`` terminates XMPP session, closes streams and sockets.


Connection status
-----------------

We can listen for changing status of connection:

.. code:: kotlin

   halcyon.eventBus.register(HalcyonStateChangeEvent) { stateChangeEvent ->
       println("Halcyon state: ${stateChangeEvent.oldState}->${stateChangeEvent.newState}")
   }

Available states:

-  ``Connecting`` - this state means, that method ``connect()`` was called, and connection to server is in progress.

-  ``Connected`` - connection is fully established.

-  ``Disconnecting`` - connection is closing because of error or manual disconnecting.

-  ``Disconnected`` - Halcyon is disconnected from XMPP server, but it is still active. It may start reconnecting to server automatically.

-  ``Stopped`` - Halcyon is turned off (not active).


.. _JSSE: https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html
.. _BouncyCastle: https://www.bouncycastle.org/java.html