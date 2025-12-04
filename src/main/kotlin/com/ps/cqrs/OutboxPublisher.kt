package com.ps.cqrs

import com.ps.cqrs.events.DomainEventPublisher

/**
 * Pull-based publisher that reads messages from a [MessageOutboxRepository]
 * and publishes their events via the provided [DomainEventPublisher].
 *
 * Typical flow:
 * 1. Handlers emit [com.ps.cqrs.domain.events.DomainEvent]s via [com.ps.cqrs.command.CommandResult.events]
 * 2. [com.ps.cqrs.middleware.OutboxMiddleware] stores them in the outbox (same transaction)
 * 3. A background job periodically invokes this publisher
 *
 * ## Example
 * ```kotlin
 * val outbox: MessageOutboxRepository = InMemoryOutbox()
 * val publisher: DomainEventPublisher = DomainEventPublisher { event ->
 *     // e.g. send to Kafka, SNS, WebSocket, etc.
 *     println("published $event")
 * }
 *
 * val outboxPublisher = OutboxPublisher(outbox, publisher)
 * outboxPublisher.publishPendingEvents(batchSize = 200)
 * ```
 */
class OutboxPublisher(
    private val outboxRepository: MessageOutboxRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    /**
     * Publishes up to [batchSize] pending events.
     *
     * For each message: on successful publish -> mark as published; on failure -> increment retry.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun publishPendingEvents(batchSize: Int = 100) {
        val messages = outboxRepository.findUnpublished(limit = batchSize)
        for (message in messages) {
            try {
                eventPublisher.publish(message.event)
                outboxRepository.markAsPublished(message.id)
            } catch (e: Exception) {
                println("Failed to publish event ${message.id}: ${e.message}")
                outboxRepository.incrementRetryCount(message.id)
            }
        }
    }
}
