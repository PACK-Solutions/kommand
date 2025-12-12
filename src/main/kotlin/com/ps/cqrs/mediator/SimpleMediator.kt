package com.ps.cqrs.mediator

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandHandler
import com.ps.cqrs.command.CommandResult
import com.ps.cqrs.middleware.CommandMiddleware
import com.ps.cqrs.middleware.OutboxMiddleware
import com.ps.cqrs.middleware.TransactionMiddleware
import com.ps.cqrs.query.Query
import com.ps.cqrs.query.QueryHandler
import com.ps.cqrs.query.QueryMiddleware
import kotlin.reflect.KClass

/**
 * Simple mediator that routes commands and queries to their handlers through middleware pipelines.
 *
 * Features
 * - Command and query pipelines are independent (you may configure different middlewares for each)
 * - Type-based routing using the exact KClass of the request object
 * - Safety checks for command middleware ordering (Transaction before Outbox)
 *
 * Example
 * ```kotlin
 * // Build directly
 * val mediator: Mediator = SimpleMediator(
 *     commandHandlers = mapOf(OpenAccount::class to OpenAccountHandler(account)),
 *     commandMiddlewares = listOf(TransactionMiddleware(NoopTransactionManager)),
 *     queryHandlers = mapOf(GetAccountBalanceQuery::class to GetAccountBalanceQueryHandler(readModel))
 * )
 *
 * // Or, prefer the DSL
 * val viaDsl = MediatorDsl.buildMediator(
 *     commandMiddlewares = listOf(TransactionMiddleware(NoopTransactionManager))
 * ) {
 *     handle(OpenAccountHandler(account))
 *     handle(GetAccountBalanceQueryHandler(readModel))
 * }
 *
 * // Usage
 * val r = mediator.send(OpenAccount(AccountId("acc-1"), 100))
 * val balance: Long = mediator.ask(GetAccountBalanceQuery(AccountId("acc-1")))
 * ```
 */
class SimpleMediator(
    private val commandHandlers: Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>> = emptyMap(),
    commandMiddlewares: List<CommandMiddleware> = emptyList(),
    private val queryHandlers: Map<KClass<out Query>, QueryHandler<*, *>> = emptyMap(),
    queryMiddlewares: List<QueryMiddleware> = emptyList(),
) : Mediator {

    private val commandChain: suspend (Command<Any?>) -> CommandResult<Any?>
    private val queryChain: suspend (Query) -> Any?

    init {
        validateCommandMiddlewareOrdering(commandMiddlewares)

        // Build chains using foldRight for a more functional, idiomatic style
        val baseCommand: suspend (Command<Any?>) -> CommandResult<Any?> = { cmd -> dispatchCommand(cmd) }
        commandChain = commandMiddlewares.foldRight(baseCommand) { mw, next ->
            {
                    cmd ->
                mw.invoke(cmd, next)
            }
        }

        val baseQuery: suspend (Query) -> Any? = { q -> dispatchQuery(q) }
        queryChain = queryMiddlewares.foldRight(baseQuery) { mw, next ->
            {
                    q ->
                mw.invoke(q, next)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatchCommand(command: Command<Any?>): CommandResult<Any?> {
        val handler = commandHandlers[command::class]
            ?: error(missingCommandHandlerMessage(command::class))
        val typed = handler as CommandHandler<Command<Any?>, Any?>
        return typed.handle(command)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatchQuery(query: Query): Any? {
        val handler = queryHandlers[query::class]
            ?: error(missingQueryHandlerMessage(query::class))
        val typed = handler as QueryHandler<Query, Any?>
        return typed.invoke(query)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> send(command: Command<R>): CommandResult<R> =
        commandChain(command as Command<Any?>) as CommandResult<R>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> ask(query: Query): R = queryChain(query) as R

    private fun validateCommandMiddlewareOrdering(middlewares: List<CommandMiddleware>) {
        val outboxes = middlewares.filterIsInstance<OutboxMiddleware>()
        require(outboxes.size <= 1) {
            "OutboxMiddleware is registered ${outboxes.size} times. It must appear at most once."
        }
        val txIndex = middlewares.indexOfFirst { it is TransactionMiddleware }
        val outboxIndex = middlewares.indexOfFirst { it is OutboxMiddleware }
        if (txIndex >= 0 && outboxIndex >= 0) {
            require(txIndex < outboxIndex) {
                "TransactionMiddleware must be placed before OutboxMiddleware to ensure outbox atomicity."
            }
        }
    }

    private fun missingCommandHandlerMessage(command: KClass<*>): String {
        val known = if (commandHandlers.isEmpty()) {
            "<none>"
        } else {
            commandHandlers.keys.joinToString {
                it.simpleName ?: it.toString()
            }
        }
        return "No handler registered for command $command. Registered command types: $known. " +
            "Ensure you registered the handler keyed by the command KClass or use MediatorDsl.buildMediator { handle(...) }."
    }

    private fun missingQueryHandlerMessage(query: KClass<*>): String {
        val known = if (queryHandlers.isEmpty()) {
            "<none>"
        } else {
            queryHandlers.keys.joinToString {
                it.simpleName ?: it.toString()
            }
        }
        return "No handler registered for query $query. Registered query types: $known. " +
            "Ensure you registered the handler keyed by the query KClass or use MediatorDsl.buildMediator { handle(...) }."
    }
}
