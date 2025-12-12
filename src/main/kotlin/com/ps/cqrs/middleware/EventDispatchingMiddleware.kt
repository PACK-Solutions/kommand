package com.ps.cqrs.middleware

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult
import com.ps.cqrs.events.EventDispatcher

/**
 * Middleware that synchronously dispatches emitted domain events to registered
 * [com.ps.cqrs.events.DomainEventHandler]s via the provided [EventDispatcher].
 *
 * This enables in-process, synchronous projections (updating read models) right
 * after a command is handled. When combined with [OutboxMiddleware], place this
 * middleware AFTER the outbox so that events are persisted before being
 * dispatched to projections.
 *
 * Example
 * ```kotlin
 * // 1) Prepare your read model and register projection handlers
 * val readModel = AccountReadModel()
 * val dispatcher = com.ps.cqrs.events.EventDispatcher()
 * dispatcher.register(AccountOpened::class, object : com.ps.cqrs.events.DomainEventHandler<AccountOpened> {
 *     override suspend fun handle(event: AccountOpened) { readModel.apply(event) }
 * })
 * dispatcher.register(MoneyDeposited::class, object : com.ps.cqrs.events.DomainEventHandler<MoneyDeposited> {
 *     override suspend fun handle(event: MoneyDeposited) { readModel.apply(event) }
 * })
 * dispatcher.register(MoneyWithdrawn::class, object : com.ps.cqrs.events.DomainEventHandler<MoneyWithdrawn> {
 *     override suspend fun handle(event: MoneyWithdrawn) { readModel.apply(event) }
 * })
 *
 * // 2) Build the mediator with Transaction -> Outbox -> EventDispatching ordering
 * val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
 *     commandMiddlewares = listOf(
 *         com.ps.cqrs.middleware.TransactionMiddleware(com.ps.cqrs.NoopTransactionManager),
 *         com.ps.cqrs.middleware.OutboxMiddleware(/* your outbox repo */ object: com.ps.cqrs.MessageOutboxRepository {
 *             override suspend fun save(event: com.ps.cqrs.domain.events.DomainEvent) = com.ps.cqrs.MessageId("1")
 *             override suspend fun findUnpublished(limit: Int) = emptyList<com.ps.cqrs.OutboxMessage>()
 *             override suspend fun markAsPublished(id: com.ps.cqrs.MessageId) {}
 *             override suspend fun incrementRetryCount(id: com.ps.cqrs.MessageId) {}
 *         }),
 *         com.ps.cqrs.middleware.EventDispatchingMiddleware(dispatcher), // must come after OutboxMiddleware
 *     )
 * ) {
 *     handle(OpenAccountHandler(/* your aggregate */))
 *     handle(GetAccountBalanceQueryHandler(readModel))
 * }
 *
 * // 3) Execute a command, then query immediately — projection is up-to-date
 * kotlinx.coroutines.runBlocking {
 *     mediator.send(OpenAccount(AccountId("acc-1"), initial = 100))
 *     val balance: Long = mediator.ask(GetAccountBalanceQuery(AccountId("acc-1")))
 *     println(balance)
 * }
 * ```
 */
class EventDispatchingMiddleware(
    private val dispatcher: EventDispatcher,
) : CommandMiddleware {
    override suspend fun <R> invoke(
        command: Command<R>,
        next: suspend (Command<R>) -> CommandResult<R>,
    ): CommandResult<R> {
        val result = next(command)
        // Synchronously project to read models/handlers
        for (event in result.events) {
            dispatcher.dispatch(event)
        }
        return result
    }
}
