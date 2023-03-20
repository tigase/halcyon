Requests
========

Each module may perform some requests on other XMPP entities, and (if yes) must return ``RequestBuilder`` object to allow check status of request and receive response.

For example, suppose we want to ping XMPP server (as described in `XEP-0199 <https://xmpp.org/extensions/xep-0199.html>`__):

**Sample ping request and response.**

.. code:: xml

   <!-- Client sends: -->
   <iq to='tigase.net' id='ping-1' type='get'>
     <ping xmlns='urn:xmpp:ping'/>
   </iq>

   <!-- Client receives: -->
   <iq from='tigase.net' to='client@tigase.net' id='ping-1' type='result'/>

There is module ``PingModule`` in Halcyon to do it:

.. code:: kotlin

   import tigase.halcyon.core.xmpp.modules.PingModule

   val pingModule: PingModule = client.getModule(PingModule)
   val request = pingModule.ping("tigase.net".toJID()).send()

In this case, method ``ping()`` returns ``RequestBuilder`` to allow add result handler, change default timeout and other operations. To send stanza you have to call method 'send()'. There is also available method ``build()`` what also creates request object, but doesn’t sends it.

.. note::

   On JVM, methods of handler will be called from separate thread.

Most universal way to receive result in asynchronous way is add response handler to request builder:

.. code:: kotlin

   val client = Halcyon()
   val pingModule: PingModule = client.getModule(PingModule)
   pingModule.ping("tigase.net".toJID()).response { result ->
       result.onSuccess { pong ->
           println("Pong: ${pong.time}ms")
       }
       result.onFailure { error ->
           println("Error $error")
       }
   }.send()
