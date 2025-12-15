package com.ps.cqrs.fixtures

import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.outbox.MessageId
import com.ps.cqrs.outbox.MessageOutboxRepository
import com.ps.cqrs.outbox.OutboxMessage

// ---- In-memory Outbox for tests ----
class InMemoryOutbox : MessageOutboxRepository {
    private val messages = mutableListOf<OutboxMessage>()

    override suspend fun save(event: DomainEvent): MessageId {
        val id = MessageId("m-${messages.size + 1}")
        messages += OutboxMessage(id = id, event = event)
        return id
    }

    override suspend fun findUnpublished(limit: Int): List<OutboxMessage> = messages.take(limit)
    override suspend fun markAsPublished(id: MessageId) {
        /* test impl */
    }

    override suspend fun incrementRetryCount(id: MessageId) {
        /* test impl */
    }
}
