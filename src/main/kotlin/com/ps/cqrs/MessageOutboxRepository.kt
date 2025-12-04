package com.ps.cqrs

import com.ps.cqrs.domain.events.DomainEvent
import java.time.Instant

/**
 * Repository abstraction for the Outbox pattern.
 *
 * Implement this interface to persist domain events as outbox messages so they can
 * be published later by an [OutboxPublisher]. The repository is typically used by
 * [com.ps.cqrs.middleware.OutboxMiddleware] inside the same transaction as command handling.
 *
 * ## Example
 * ```kotlin
 * class InMemoryOutbox : MessageOutboxRepository {
 *     private val messages = mutableListOf<OutboxMessage>()
 *
 *     override fun save(event: DomainEvent): MessageId {
 *         val id = MessageId("m-${messages.size + 1}")
 *         messages += OutboxMessage(id = id, event = event)
 *         return id
 *     }
 *
 *     override fun findUnpublished(limit: Int): List<OutboxMessage> = messages.take(limit)
 *     override fun markAsPublished(id: MessageId) { /* update state */ }
 *     override fun incrementRetryCount(id: MessageId) { /* update state */ }
 * }
 * ```
 */
interface MessageOutboxRepository {
    /** Persist a new [event] into the outbox and return its generated id. */
    suspend fun save(event: DomainEvent): MessageId

    /** Find unpublished messages up to [limit]. */
    suspend fun findUnpublished(limit: Int = 100): List<OutboxMessage>

    /** Mark a message as published. */
    suspend fun markAsPublished(id: MessageId)

    /** Increment retry counter for a failed message. */
    suspend fun incrementRetryCount(id: MessageId)
}

/**
 * A message stored in the outbox.
 */
data class OutboxMessage(
    val id: MessageId,
    val event: DomainEvent,
    val retryCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val published: Boolean = false,
)

@JvmInline
value class MessageId(val value: String)
