package com.ps

/**
 * Dispatches [Command]s to their corresponding handlers.
 *
 * Implementations are responsible for locating the appropriate [CommandHandler]
 * and orchestrating any [CommandMiddleware] that should run before/after the handler.
 */
interface CommandBus {
    /**
     * Executes the given [command].
     *
     * Returns the full [CommandResult] containing the success/error result and any emitted [DomainEvent]s.
     * Middlewares may short‑circuit and/or transform the returned result.
     *
     * @param R the success type associated with the command
     * @param command the command instance to execute
     * @return the [CommandResult] produced by the middleware chain/handler
     */
    fun <R> execute(command: Command<R>): CommandResult<R>
}
