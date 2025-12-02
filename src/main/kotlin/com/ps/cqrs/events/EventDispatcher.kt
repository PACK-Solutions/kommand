package com.ps.cqrs.events

import com.ps.cqrs.domain.events.DomainEvent
import kotlin.reflect.KClass

/**
 * Simple in-memory dispatcher that routes domain events to their registered handlers.
 *
 * The `EventDispatcher` maintains a mapping of event types to a list of `DomainEventHandler`
 * instances. When an event is dispatched, all handlers registered for that event's concrete
 * class are invoked in registration order.
 *
 * Characteristics:
 * - One-to-many: multiple handlers can be registered per event type
 * - In-memory: suitable for simple apps, tests, or as a building block behind a message bus
 * - Coroutine-friendly: dispatching is `suspend` and calls `suspend` handlers
 *
 * Thread-safety: This implementation uses a mutable map and is not thread-safe for concurrent
 * registration and dispatch. If you need concurrent access, protect it with external
 * synchronization or replace with a concurrent structure.
 *
 * Error handling: Exceptions thrown by a handler will propagate to the caller and stop
 * subsequent handlers from running. Wrap `dispatch` yourself if you need per-handler isolation.
 *
 * Sample usage:
 * ```kotlin
 * // 1) Define an event
 * data class UserRegisteredEvent(val userId: String) : DomainEvent
 *
 * // 2) Implement a handler
 * class SendWelcomeEmailHandler(private val emailService: EmailService) : DomainEventHandler<UserRegisteredEvent> {
 *     override suspend fun handle(event: UserRegisteredEvent) {
 *         emailService.sendWelcome(event.userId)
 *     }
 * }
 *
 * // 3) Register and dispatch
 * val dispatcher = EventDispatcher()
 * dispatcher.register(UserRegisteredEvent::class, SendWelcomeEmailHandler(emailService))
 *
 * // Later in your code (e.g., after a command succeeds):
 * runBlocking {
 *     dispatcher.dispatch(UserRegisteredEvent(userId = "u-123"))
 * }
 * ```
 */
class EventDispatcher {
    private val handlers = mutableMapOf<KClass<out DomainEvent>, MutableList<DomainEventHandler<*>>>()

    /**
     * Registers a handler to be invoked when events of type [eventClass] are dispatched.
     * Multiple handlers can be registered for the same event type; they will be invoked
     * in the order they were registered.
     */
    fun <E : DomainEvent> register(eventClass: KClass<E>, handler: DomainEventHandler<E>) {
        handlers.getOrPut(eventClass) { mutableListOf() }.add(handler)
    }

    /**
     * Dispatches a single [event] to all handlers registered for its concrete class.
     *
     * Notes:
     * - This method does not traverse supertypes; only handlers registered for `event::class`
     *   are invoked.
     * - If a handler throws, the exception propagates and further handlers are not invoked.
     */
    suspend fun dispatch(event: DomainEvent) {
        handlers[event::class]?.forEach { handler ->
            @Suppress("UNCHECKED_CAST")
            (handler as DomainEventHandler<DomainEvent>).handle(event)
        }
    }
}
