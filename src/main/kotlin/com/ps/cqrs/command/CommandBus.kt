package com.ps.cqrs.command

/**
 * Dispatches [Command]s to their corresponding handlers.
 *
 * Implementations are responsible for locating the appropriate [CommandHandler]
 * and orchestrating any [com.ps.cqrs.CommandMiddleware] that should run before/after the handler.
 *
 * ## Example
 * ```kotlin
 * // Define a command and handler
 * data class CreateUser(val email: String) : Command<UserId>
 *
 * class CreateUserHandler : CommandHandler<CreateUser, UserId> {
 *     override fun handle(command: CreateUser): CommandResult<UserId> {
 *         val id = UserId("123")
 *         val events = listOf(UserCreatedEvent(command.email))
 *         return CommandResult(result = Ok(id), events = events)
 *     }
 * }
 *
 * // Minimal bus using SimpleCommandBus
 * val handler = CreateUserHandler()
 * val cmd = CreateUser("a@b.com")
 * val bus: CommandBus = SimpleCommandBus(
 *     handlers = mapOf(CreateUser::class to handler)
 * )
 *
 * val result: CommandResult<UserId> = bus.execute(cmd)
 * ```
 */
interface CommandBus {
    /**
     * Executes the given [command].
     *
     * Returns the full [CommandResult] containing the success/error
     * result and any emitted [com.ps.cqrs.domain.events.DomainEvent]s.
     * Middlewares may short‑circuit and/or transform the returned result.
     *
     * @param R the success type associated with the command
     * @param command the command instance to execute
     * @return the [CommandResult] produced by the middleware chain/handler
     */
    suspend fun <R> execute(command: Command<R>): CommandResult<R>
}
