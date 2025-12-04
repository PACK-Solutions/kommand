package com.ps.cqrs.middleware

import com.ps.cqrs.TransactionManager
import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult

/**
 * Middleware that ensures command handling (and inner middlewares like [OutboxMiddleware])
 * execute within a transaction boundary provided by [com.ps.cqrs.TransactionManager].
 *
 * Example
 * ```kotlin
 * val txManager: TransactionManager = NoopTransactionManager
 * val bus = SimpleCommandBus(
 *     handlers = mapOf(
 *     /* YourCommand::class to YourHandler() */),
 *     // Place TransactionMiddleware BEFORE OutboxMiddleware
 *     middlewares = listOf(
 *     TransactionMiddleware(txManager),
 *     OutboxMiddleware(/* outbox */ object: MessageOutboxRepository {
 *         override suspend fun save(event: com.ps.cqrs.domain.events.DomainEvent) = MessageId("1")
 *         override suspend fun findUnpublished(limit: Int) = emptyList<OutboxMessage>()
 *         override suspend fun markAsPublished(id: MessageId) {}
 *         override suspend fun incrementRetryCount(id: MessageId) {}
 *     }))
 * )
 * ```
 */
class TransactionMiddleware(
    private val transactionManager: TransactionManager,
) : CommandMiddleware {
    override suspend fun <R> invoke(
        command: Command<R>,
        next: suspend (Command<R>) -> CommandResult<R>,
    ): CommandResult<R> = transactionManager.withinTransaction { next(command) }
}
