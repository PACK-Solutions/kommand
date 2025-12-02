package com.ps.cqrs.command

/**
 * Handles a specific [Command] type and produces a [CommandResult].
 *
 * Implementations contain the application logic for a given command and may
 * emit [com.ps.cqrs.domain.events.DomainEvent]s via the returned [CommandResult].
 *
 * @param C the concrete command type
 * @param R the success value type produced when the command completes
 *
 * ## Example
 * ```kotlin
 * data class Register(val email: String) : Command<UserId>
 * data class Registered(val email: String) : DomainEvent
 *
 * class RegisterHandler : CommandHandler<Register, UserId> {
 *     override fun handle(command: Register): CommandResult<UserId> {
 *         val id = UserId("u-1")
 *         return CommandResult(
 *             result = Ok(id),
 *             events = listOf(Registered(command.email))
 *         )
 *     }
 * }
 * ```
 */
interface CommandHandler<C : Command<R>, R> {
    /**
     * Executes the logic for [command] and returns a [CommandResult].
     *
     * Implementations should be side‑effecting only as required by the command.
     * Any domain events produced by handling should be returned in the result
     * rather than published directly, enabling middleware to observe/augment.
     */
    suspend fun handle(command: C): CommandResult<R>
}
