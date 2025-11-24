package com.ps

/**
 * Handles a specific [Command] type and produces a [CommandResult].
 *
 * Implementations contain the application logic for a given command and may
 * emit [DomainEvent]s via the returned [CommandResult].
 *
 * @param C the concrete command type
 * @param R the success value type produced when the command completes
 */
interface CommandHandler<C : Command<R>, R> {
    /**
     * Executes the logic for [command] and returns a [CommandResult].
     *
     * Implementations should be side‑effecting only as required by the command.
     * Any domain events produced by handling should be returned in the result
     * rather than published directly, enabling middleware to observe/augment.
     */
    fun handle(command: C): CommandResult<R>
}
