package com.ps.cqrs.middleware

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandResult

/**
 * Middleware that can observe, short‑circuit, or augment command handling.
 *
 * Middlewares are composed into a chain by [com.ps.cqrs.command.SimpleCommandBus] so that each
 * middleware can run logic before and/or after the next component in the
 * chain (another middleware or the final [com.ps.cqrs.command.CommandHandler]).
 *
 * ## Example
 * ```kotlin
 * // Simple logging middleware
 * class LoggingMiddleware : CommandMiddleware {
 *     override suspend fun <R> invoke(
 *         command: Command<R>,
 *         next: suspend (Command<R>) -> CommandResult<R>
 *     ): CommandResult<R> {
 *         println("Handling: ${command::class.simpleName}")
 *         val result = next(command)
 *         println("Handled with result: ${result.result}")
 *         return result
 *     }
 * }
 *
 * val bus = SimpleCommandBus(
 *     handlers = mapOf(/* YourCommand::class to YourCommandHandler() */),
 *     middlewares = listOf(LoggingMiddleware())
 * )
 * ```
 */
interface CommandMiddleware {
    /**
     * Intercepts the execution of [command]. Call [next] to continue the chain
     * or return a [CommandResult] directly to short‑circuit.
     *
     * Implementations may transform the returned result, for example,
     * by appending [com.ps.cqrs.domain.events.DomainEvent]s.
     *
     * @param R the success type carried by the command
     * @param command the command instance being executed
     * @param next the function that invokes the next middleware or handler
     * @return the (possibly transformed) [CommandResult]
     */
    suspend fun <R> invoke(
        command: Command<R>,
        next: suspend (Command<R>) -> CommandResult<R>,
    ): CommandResult<R>
}
