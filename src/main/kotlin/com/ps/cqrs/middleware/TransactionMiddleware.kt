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
 * val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
 *     // Place TransactionMiddleware BEFORE OutboxMiddleware
 *     commandMiddlewares = listOf(
 *         TransactionMiddleware(txManager),
 *         OutboxMiddleware(/* outbox */ object: com.ps.cqrs.MessageOutboxRepository {
 *             override suspend fun save(event: com.ps.cqrs.domain.events.DomainEvent) = com.ps.cqrs.MessageId("1")
 *             override suspend fun findUnpublished(limit: Int) = emptyList<com.ps.cqrs.OutboxMessage>()
 *             override suspend fun markAsPublished(id: com.ps.cqrs.MessageId) {}
 *             override suspend fun incrementRetryCount(id: com.ps.cqrs.MessageId) {}
 *         })
 *     )
 * ) {
 *     handle(/* YourCommandHandler() */)
 * }
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
