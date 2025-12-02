package com.ps.cqrs.command

/**
 * Marker interface for command‑level errors.
 *
 * Implement your domain‑specific error types by implementing this interface
 * and return them via [CommandResult.result] using `Result.Err`.
 *
 * ## Example
 * ```kotlin
 * import com.github.michaelbull.result.*
 *
 * data class ValidationError(val message: String) : CommandError
 *
 * class CreateHandler : CommandHandler<Create, Unit> {
 *     override fun handle(command: Create): CommandResult<Unit> {
 *         return if (command.name.isBlank()) {
 *             CommandResult(result = Err(ValidationError("name cannot be blank")))
 *         } else {
 *             CommandResult(result = Ok(Unit))
 *         }
 *     }
 * }
 * ```
 */
interface CommandError
