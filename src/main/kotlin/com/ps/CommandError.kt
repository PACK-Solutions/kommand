package com.ps

/**
 * Marker interface for command‑level errors.
 *
 * Implement your domain‑specific error types by implementing this interface
 * and return them via [CommandResult.result] using `Result.Err`.
 */
interface CommandError
