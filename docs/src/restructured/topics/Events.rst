Events
======

Halcyon is events driven library. It means you have to listen for events
to receive message, react for disconnection or so. There is single
events bus inside, to which you can register listeners. Each part of
library (like modules, connectors, etc.) may have set of own events to
fire.

General code to registering events:

.. code:: kotlin

   halcyon.eventBus.register<EVENT_TYPE>(EVENT_NAME) { event ->
   …
   }

In Halcyon, name of event is defined as constant variable named ``TYPE``
in each event.

For example:

.. code:: kotlin

   halcyon.eventBus.register<ReceivedXMLElementEvent>(ReceivedXMLElementEvent.TYPE) { event ->
       println(" >>> ${event.element.getAsString()}")
   }

You can use EventBus for you own applications. No need to register
events types. Just create object inherited from
``tigase.halcyon.core.eventbus.Event`` and call method
``eventbus.fire()``:

.. code:: kotlin

   data class SampleEvent(val sampleData: String) : Event(TYPE){

       companion object {
           const val TYPE = "sampleEvent"
       }

   }

   halcyon.eventBus.fire(SampleEvent("test"))