package com.ps.cqrs.mediator

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult
import com.ps.cqrs.query.Query

/**
 * Mediator interface inspired by MediatR (C#).
 *
 * Acts as a central hub to route requests to their handlers through optional pipelines.
 * This project models two request kinds:
 * - Commands: write operations that return a [CommandResult]
 * - Queries: read operations that return a value of type [R]
 *
 * Example
 * ```kotlin
 * // Build a mediator with handlers and middlewares
 * val mediator = MediatorDsl.buildMediator(
 *     commandMiddlewares = listOf(/* CommandMiddleware */),
 *     queryMiddlewares = listOf(/* QueryMiddleware */)
 * ) {
 *     // Register handlers type-safely
 *     handle(OpenAccountHandler(account))
 *     handle(GetAccountBalanceQueryHandler(readModel))
 * }
 *
 * // Send a command
 * val r: CommandResult<Unit> = mediator.send(OpenAccount(AccountId("acc-1"), initial = 100))
 *
 * // Ask a query
 * val balance: Long = mediator.ask(GetAccountBalanceQuery(AccountId("acc-1")))
 * ```
 */
interface Mediator {
    /** Send a command through the command pipeline and return its [CommandResult]. */
    suspend fun <R> send(command: Command<R>): CommandResult<R>

    /** Send a query through the query pipeline and return its value. */
    suspend fun <R> ask(query: Query): R
}
