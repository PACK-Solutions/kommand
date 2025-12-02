package com.ps.cqrs

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult

/**
 * Middleware that persists emitted domain events to a [MessageOutboxRepository].
 *
 * This lets you keep handler code focused on producing events while ensuring
 * they are captured for later publication by [OutboxPublisher].
 *
 * ## Example
 * ```kotlin
 * // Configure the bus with the outbox middleware
 * val outboxRepo: MessageOutboxRepository = InMemoryOutbox()
 * val bus = SimpleCommandBus(
 *     handlers = mapOf(/* command instance to handler */),
 *     middlewares = listOf(OutboxMiddleware(outboxRepo))
 * )
 *
 * // Later, publish them out-of-band
 * val eventPublisher: DomainEventPublisher = DomainEventPublisher { println(it) }
 * OutboxPublisher(outboxRepo, eventPublisher).publishPendingEvents()
 * ```
 */
class OutboxMiddleware(
    private val outboxRepository: MessageOutboxRepository,
) : CommandMiddleware {
    override suspend fun <R> invoke(
        command: Command<R>,
        next: suspend (Command<R>) -> CommandResult<R>
    ): CommandResult<R> {
        val result = next(command)
        result.events.forEach { outboxRepository.save(it) }
        return result
    }
}
