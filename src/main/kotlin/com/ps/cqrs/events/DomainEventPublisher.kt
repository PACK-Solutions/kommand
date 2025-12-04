package com.ps.cqrs.events

import com.ps.cqrs.domain.events.DomainEvent

/**
 * Publishes [DomainEvent]s to the outside world (e.g. messaging, listeners).
 *
 * In applications that use the outbox pattern, you typically persist events
 * to an outbox within the same transaction as your command handling, and then
 * use an [com.ps.cqrs.OutboxPublisher] to call into this publisher out of band.
 *
 * ## Example
 * ```kotlin
 * // Simple implementation that logs to stdout
 * val publisher = DomainEventPublisher { event ->
 *     println("Publishing event: $event")
 * }
 *
 * // With OutboxPublisher driving the process
 * val outboxRepo: MessageOutboxRepository = InMemoryOutbox()
 * val outboxPublisher = OutboxPublisher(outboxRepo, publisher)
 * outboxPublisher.publishPendingEvents(batchSize = 50)
 * ```
 */
fun interface DomainEventPublisher {
    suspend fun publish(event: DomainEvent)
}
