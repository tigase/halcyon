Events
======

Halcyon is events driven library. It means you have to listen for events
to receive message, react for disconnection or so. There is single
events bus inside, to which you can register listeners. Each part of
library (like modules, connectors, etc.) may have set of own events to
fire.

General code to registering events:

.. code:: kotlin

   halcyon.eventBus.register(Event) { event ->

   }

You can use EventBus for you own applications. No need to register
events before. Just create object inherited from
``tigase.halcyon.core.eventbus.Event`` and call method
``eventbus.fire()``:

.. code:: kotlin

   data class SampleEvent(val sampleData: String) : Event(TYPE){

       companion object : EventDefinition<SampleEvent> {
           override val TYPE = "sampleEvent"
       }

   }

   halcyon.eventBus.fire(SampleEvent("test"))

Remember that ``TYPE`` must be unique string, because it is the identifier of event in Event Bus.


Using ``EventDefinition`` interface for companion object is very useful, because when you register events listener,
no need to declare type of observer events:

.. code:: kotlin

   halcyon.eventBus.register(SampleEvent) { event ->

   }

