package com.ps.cqrs.command

/**
 * Marker interface for a Command handled via the Mediator.
 *
 * A command models an intention to perform an action. Each concrete command
 * specifies the type parameter [R] to indicate the type of value the caller
 * expects back when the command is executed by the mediator.
 *
 * Typical usage is to declare small immutable data classes implementing this interface.
 *
 * ## Example
 * ```kotlin
 * // Define a command that expects a String on success
 * data class SayHelloCommand(val name: String) : Command<String>
 *
 * // Provide a handler and register it in the Mediator DSL
 * val handler = object : CommandHandler<SayHelloCommand, String> {
 *     override suspend fun handle(command: SayHelloCommand): CommandResult<String> {
 *         return CommandResult(result = Ok("Hello, ${command.name}!"))
 *     }
 * }
 *
 * val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator {
 *     handle(handler)
 * }
 *
 * // Later, execute any instance of the command type
 * val result: CommandResult<String> = kotlinx.coroutines.runBlocking {
 *     mediator.send(SayHelloCommand("world"))
 * }
 * ```
 *
 * The mediator routes by command type (`KClass`).
 *
 * @param R the expected success value type produced by executing this command
 */
interface Command<R>
