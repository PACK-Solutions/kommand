package com.ps.cqrs.middleware

import com.ps.cqrs.MessageOutboxRepository
import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult

/**
 * Middleware that persists emitted domain events to a [com.ps.cqrs.MessageOutboxRepository].
 *
 * This lets you keep handler code focused on producing events while ensuring
 * they are captured for later publication by [com.ps.cqrs.OutboxPublisher].
 *
 * ## Example
 * ```kotlin
 * // Configure the mediator with the outbox middleware
 * val outboxRepo: MessageOutboxRepository = InMemoryOutbox()
 * val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
 *     commandMiddlewares = listOf(OutboxMiddleware(outboxRepo))
 * ) {
 *     handle(/* YourCommandHandler() */)
 * }
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
        next: suspend (Command<R>) -> CommandResult<R>,
    ): CommandResult<R> {
        val result = next(command)
        result.events.forEach { outboxRepository.save(it) }
        return result
    }
}
