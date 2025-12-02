package com.ps.cqrs.command

import com.github.michaelbull.result.Result
import com.ps.cqrs.domain.events.DomainEvent

/**
 * The outcome of handling a [Command].
 *
 * Contains the success/error [result] and any domain [events] emitted during handling.
 * Middleware can observe and transform both the result and the event list.
 *
 * ## Example
 * ```kotlin
 * // Using michaelbull/result
 * import com.github.michaelbull.result.*
 *
 * // Build a successful result with one event
 * data class UserCreated(val email: String) : DomainEvent
 *
 * val ok: CommandResult<String> = CommandResult(
 *     result = Ok("user-id"),
 *     events = listOf(UserCreated("a@b.com"))
 * )
 *
 * // Build an error result
 * data class ValidationError(val message: String) : CommandError
 * val err: CommandResult<String> = CommandResult(
 *     result = Err(ValidationError("invalid email"))
 * )
 *
 * // Middleware can append events
 * val enriched = ok.copy(events = ok.events + listOf(UserCreated("c@d.com")))
 * ```
 *
 * @param R the success value type
 * @property result the success or error produced by the handler
 * @property events domain events emitted while handling the command
 */
data class CommandResult<R>(
    val result: Result<R, CommandError>,
    val events: List<DomainEvent> = emptyList(),
)
