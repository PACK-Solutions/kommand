package com.ps.cqrs.middleware

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult
import com.ps.cqrs.transaction.TransactionManager

/**
 * Middleware that ensures command handling (and inner middlewares like [OutboxMiddleware])
 * execute within a transaction boundary provided by [TransactionManager].
 *
 * Example
 * ```kotlin
 * val txManager: TransactionManager = NoopTransactionManager
 * val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
 *     // Place TransactionMiddleware BEFORE OutboxMiddleware
 *     commandMiddlewares = listOf(
 *         TransactionMiddleware(txManager),
 *         OutboxMiddleware(/* outbox */ object: com.ps.cqrs.outbox.MessageOutboxRepository {
 *             override suspend fun save(event: com.ps.cqrs.domain.events.DomainEvent) = com.ps.cqrs.outbox.MessageId("1")
 *             override suspend fun findUnpublished(limit: Int) = emptyList<com.ps.cqrs.outbox.OutboxMessage>()
 *             override suspend fun markAsPublished(id: com.ps.cqrs.outbox.MessageId) {}
 *             override suspend fun incrementRetryCount(id: com.ps.cqrs.outbox.MessageId) {}
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
