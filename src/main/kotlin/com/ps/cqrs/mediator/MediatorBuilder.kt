package com.ps.cqrs.mediator

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandHandler
import com.ps.cqrs.middleware.CommandMiddleware
import com.ps.cqrs.query.Query
import com.ps.cqrs.query.QueryHandler
import com.ps.cqrs.query.QueryMiddleware
import kotlin.reflect.KClass

/**
 * Builder/DSL for configuring a [Mediator] with command and query handlers and middlewares.
 *
 * Supports:
 * - Registering handlers via type‑safe `handle()` overloads
 * - Building a [Mediator]
 *
 * Example
 * ```kotlin
 * // Using the DSL entry point
 * val mediator = MediatorDsl.buildMediator(
 *     commandMiddlewares = listOf(TransactionMiddleware(NoopTransactionManager)),
 *     queryMiddlewares = listOf(/* QueryMiddleware */)
 * ) {
 *     handle(OpenAccountHandler(account))
 *     handle(GetAccountBalanceQueryHandler(readModel))
 * }
 *
 * // Send commands / ask queries
 * val r = mediator.send(OpenAccount(AccountId("acc-1"), 100))
 * val balance: Long = mediator.ask(GetAccountBalanceQuery(AccountId("acc-1")))
 *
 * // The Mediator is the single public entry point
 * ```
 */
class MediatorBuilder {
    @PublishedApi
    internal val commandHandlers: MutableMap<KClass<out Command<*>>, CommandHandler<out Command<*>, *>> = mutableMapOf()

    @PublishedApi
    internal val queryHandlers: MutableMap<KClass<out Query>, QueryHandler<*, *>> = mutableMapOf()

    // Type‑safe registration helpers
    inline fun <reified C : Command<R>, R> handle(handler: CommandHandler<C, R>) {
        commandHandlers[C::class] = handler
    }

    inline fun <reified Q : Query, R> handle(handler: QueryHandler<Q, R>) {
        queryHandlers[Q::class] = handler
    }

    // Build a mediator with optional independent pipelines for commands and queries
    fun buildMediator(
        commandMiddlewares: List<CommandMiddleware> = emptyList(),
        queryMiddlewares: List<QueryMiddleware> = emptyList(),
    ): Mediator = SimpleMediator(
        commandHandlers = commandHandlers,
        commandMiddlewares = commandMiddlewares,
        queryHandlers = queryHandlers,
        queryMiddlewares = queryMiddlewares,
    )
}

/**
 * Convenience DSL entry points for building mediators.
 */
object MediatorDsl {
    inline fun buildMediator(
        commandMiddlewares: List<CommandMiddleware> = emptyList(),
        queryMiddlewares: List<QueryMiddleware> = emptyList(),
        configure: MediatorBuilder.() -> Unit,
    ): Mediator = MediatorBuilder().apply(configure).buildMediator(commandMiddlewares, queryMiddlewares)
}
