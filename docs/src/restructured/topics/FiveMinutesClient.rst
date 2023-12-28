Five Minutes Client
===================

Lets see how to build Echo Bot with Halcyon library.

Setting up project
------------------

We will use Gradle to build project. First of all we have to add URLs of Maven repositories (see:
:ref:`header-setting-up-client`) to ``build.gradle.kts``

.. code:: kotlin

    repositories {
        mavenCentral()
        maven("https://maven-repo.tigase.org/repository/release/")
        maven("https://maven-repo.tigase.org/repository/snapshot/")
    }

and Halcyon library to dependencies section:

.. code:: kotlin

    dependencies {
        implementation("tigase.halcyon:halcyon-core:1.0.0")
    }

Halcyon configuration
---------------------

Creating Halcyon instance and its basic configuration is very simple.

.. code:: kotlin

    val halcyon = createHalcyon {
        auth {
            userJID = "yourjid@server.com".toBareJID()
            password { "secretpassword" }
        }
    }
    halcyon.connectAndWait()

Function ``connectAndWait()`` is available only in JVM and provides synchronous connection. Default function
``connect()`` is asynchronous.

Sending messages to recipient
-----------------------------

Messages are one of the basic types of stanzas sent on the XMPP network. You don't need to to use any module just to
send stanza, you can do that directly from main Halcyon.

.. code:: kotlin

    val requestBuilder = halcyon.request.message {
        to = "recipient@server.com".toJID()
        body = "Test message"
    }
    requestBuilder.send()

As you can see, ``halcyon.request.message`` doesn't send message directly. Instead of that it prepares
``RequestBuilder``, what allows to add some listeners (like ``onSend``) to the builder.

Listening for incoming messages
-------------------------------

All events (receiving message also generates an event) are distributed among listeners by EventBus. To handle
received message, you need to register events listener:

.. code:: kotlin

    halcyon.eventBus.register(MessageReceivedEvent) {
    }

Note, that some messages exchanged between client and server may not contain any text, as they are a kind of signals
informing the client (or server) about something. That's why we need to check if incoming message contains body:

.. code:: kotlin

    halcyon.eventBus.register(MessageReceivedEvent) {
        if (!it.stanza.body.isNullOrEmpty()) {
        }
    }

Then we can send back received text:

.. code:: kotlin

    halcyon.eventBus.register(MessageReceivedEvent) {
        if (!it.stanza.body.isNullOrEmpty()) {
            halcyon.request.message {
                to = it.fromJID
                body = "Echo: ${it.stanza.body}"
            }.send()
        }
    }

Keep application running
------------------------

Because this is a very simple application and Halcyon does not maintain any threads after the application terminates,
we need to prevent the application from terminating when Halcyon is connected.
We can add loop to cyclic check Halcyon connection status:

.. code:: kotlin

    while (halcyon.state == AbstractHalcyon.State.Connected) Thread.sleep(1000)

Disconnecting bot remotely
--------------------------

Last thing we should add is feature to disconnect and terminate our application remotely. We can add code to existing
handler of incoming messages, or we can add another one:

.. code:: kotlin

    halcyon.eventBus.register(MessageReceivedEvent) {
        if (it.stanza.body == "/stop") {
            halcyon.disconnect()
        }
    }

Note, that there is no sender authorization, so anyone is able to terminate the bot.

Summary
-------

That's all.
We just created very simple bot. As you can see using XMPP network is very easy and (as usual) the most complicated
part will be business logic of application.