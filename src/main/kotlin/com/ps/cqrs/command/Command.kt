package com.ps.cqrs.command

/**
 * Marker interface for a Command in the Command Bus pattern.
 *
 * A command models an intention to perform an action. Each concrete command
 * specifies the type parameter [R] to indicate the type of value the caller
 * expects back from the bus when the command is executed.
 *
 * Typical usage is to declare small immutable data classes implementing this interface.
 *
 * ## Example
 * ```kotlin
 * // Define a command that expects a String on success
 * data class SayHelloCommand(val name: String) : Command<String>
 *
 * // Provide a handler and register it in a CommandBus (see SimpleCommandBus)
 * val handler = object : CommandHandler<SayHelloCommand, String> {
 *     override suspend fun handle(command: SayHelloCommand): CommandResult<String> {
 *         return CommandResult(result = Ok("Hello, ${command.name}!"))
 *     }
 * }
 *
 * val bus: CommandBus = SimpleCommandBus(
 *     handlers = mapOf(
 *         SayHelloCommand::class to handler
 *     )
 * )
 *
 * // Later, execute any instance of the command type
 * val result: CommandResult<String> = bus.execute(SayHelloCommand("world"))
 * ```
 *
 * `SimpleCommandBus` routes by command type (`KClass`). You can adapt/extend it for
 * different lookup strategies if needed.
 *
 * @param R the expected success value type produced by executing this command
 */
interface Command<R>
