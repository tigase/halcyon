package tigase.halcyon.rx

import com.badoo.reaktive.observable.*
import com.badoo.reaktive.subject.behavior.BehaviorSubject
import com.badoo.reaktive.subject.publish.PublishSubject
import tigase.halcyon.core.eventbus.Event
import tigase.halcyon.core.eventbus.EventBusInterface
import tigase.halcyon.core.eventbus.EventDefinition
import tigase.halcyon.core.eventbus.EventHandler

fun <T : Event> EventBusInterface.observe(definition: EventDefinition<T>): Observable<T> =
    this.observe(definition.TYPE)

fun <T : Event> EventBusInterface.observe(type: String? = null): Observable<T> {
    val subject = PublishSubject<T>()
    val handler = object : EventHandler<T> {
        override fun onEvent(event: T) {
            subject.onNext(event)
        }
    }
    return subject.doOnBeforeSubscribe {
        this@observe.register(type ?: EventBusInterface.ALL_EVENTS, handler)
    }
        .doOnBeforeDispose {
            this@observe.unregister(handler)
        }
}

fun <E : Event, T> EventBusInterface.eventUpdatedValue(
    definition: EventDefinition<E>,
    initialValue: () -> T,
    mapper: (E) -> T
): Observable<T> = this.eventUpdatedValue(definition.TYPE, initialValue, mapper)

fun <E : Event, T> EventBusInterface.eventUpdatedValue(
    event: String,
    initialValue: () -> T,
    mapper: (E) -> T
): Observable<T> {
    val subject = BehaviorSubject(initialValue())
    val disposable = this.observe<E>(event)
        .subscribe { e -> subject.onNext(mapper(e)) }
    return subject.doOnAfterDispose { disposable.dispose() }
}
