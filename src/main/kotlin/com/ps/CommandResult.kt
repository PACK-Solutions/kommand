package com.ps

import com.github.michaelbull.result.Result

/**
 * The outcome of handling a [Command].
 *
 * Contains the success/error [result] and any domain [events] emitted during handling.
 * Middleware can observe and transform both the result and the event list.
 *
 * @param R the success value type
 * @property result the success or error produced by the handler
 * @property events domain events emitted while handling the command
 */
data class CommandResult<R>(
    val result: Result<R, CommandError>,
    val events: List<DomainEvent> = emptyList(),
)
