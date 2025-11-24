package com.ps

/**
 * Marker interface for a Command in the Command Bus pattern.
 *
 * A command models an intention to perform an action. Each concrete command
 * specifies the type parameter [R] to indicate the type of value the caller
 * expects back from the bus when the command is executed.
 *
 * Typical usage is to declare small immutable data classes implementing this interface.
 *
 * @param R the expected success value type produced by executing this command
 */
interface Command<R>
