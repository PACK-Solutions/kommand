package com.ps

/**
 * Middleware that can observe, short‑circuit, or augment command handling.
 *
 * Middlewares are composed into a chain by [SimpleCommandBus] so that each
 * middleware can run logic before and/or after the next component in the
 * chain (another middleware or the final [CommandHandler]).
 */
interface CommandMiddleware {
    /**
     * Intercepts the execution of [command]. Call [next] to continue the chain
     * or return a [CommandResult] directly to short‑circuit.
     *
     * Implementations may transform the returned result, for example, by appending [DomainEvent]s.
     *
     * @param R the success type carried by the command
     * @param command the command instance being executed
     * @param next the function that invokes the next middleware or handler
     * @return the (possibly transformed) [CommandResult]
     */
    fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R>
}
