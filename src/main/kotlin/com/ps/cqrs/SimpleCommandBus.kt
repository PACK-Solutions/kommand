package com.ps.cqrs

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandBus
import com.ps.cqrs.command.CommandHandler
import com.ps.cqrs.command.CommandResult
import kotlin.reflect.KClass

/**
 * Minimal [com.ps.cqrs.command.CommandBus] implementation that composes a middleware chain and
 * dispatches commands to handlers.
 *
 * Important notes:
 * - Handler lookup is performed using the [KClass] of the concrete [com.ps.cqrs.command.Command] in [handlers].
 *   Register your handlers keyed by the command class (e.g. `CreateTodo::class`).
 * - Middlewares are composed outer-to-inner using `foldRight`, so the first middleware
 *   in the list becomes the outermost wrapper (runs before and after the rest).
 * - The [execute] method returns the full [com.ps.cqrs.command.CommandResult] from the middleware chain/handler,
 *   allowing callers to inspect both the success/error and any emitted events.
 *
 * ## Example
 * ```kotlin
 * // Define command, event, and handler
 * data class CreateTodo(val title: String) : Command<TodoId>
 * data class TodoCreated(val title: String) : DomainEvent
 * class CreateTodoHandler : CommandHandler<CreateTodo, TodoId> {
 *     override suspend fun handle(command: CreateTodo): CommandResult<TodoId> {
 *         val id = TodoId("t-1")
 *         return CommandResult(
 *             result = com.github.michaelbull.result.Ok(id),
 *             events = listOf(TodoCreated(command.title))
 *         )
 *     }
 * }
 *
 * // Register the handler keyed by the command KClass
 * val cmd = CreateTodo("Write docs")
 * val bus: CommandBus = SimpleCommandBus(
 *     handlers = mapOf(CreateTodo::class to CreateTodoHandler() as CommandHandler<out Command<*>, *>),
 *     middlewares = listOf(OutboxMiddleware(outboxRepository = /* your repo */ object: MessageOutboxRepository {
 *         override fun save(event: DomainEvent) = MessageId("1")
 *         override fun findUnpublished(limit: Int) = emptyList<OutboxMessage>()
 *         override fun markAsPublished(id: MessageId) {}
 *         override fun incrementRetryCount(id: MessageId) {}
 *     }))
 * )
 *
 * // inside a coroutine scope
 * val result: CommandResult<TodoId> = bus.execute(cmd)
 * ```
 *
 * @property handlers mapping used to resolve the handler for a given command type (KClass)
 * @param middlewares the ordered list of middlewares to wrap around dispatch
 */
class SimpleCommandBus(
    val handlers: Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>> = emptyMap(),
    middlewares: List<CommandMiddleware> = emptyList(),
) : CommandBus {

    private val chain: suspend (Command<Any?>) -> CommandResult<Any?>

    init {
        var next: suspend (Command<Any?>) -> CommandResult<Any?> = { cmd -> dispatch(cmd) }
        for (mw in middlewares.asReversed()) {
            val currentNext = next
            next = { cmd -> mw.invoke(cmd, currentNext) }
        }
        chain = next
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(command: Command<Any?>): CommandResult<Any?> {
        val handler = handlers[command::class] ?: error("No handler registered for command ${command::class}")
        val typed = handler as CommandHandler<Command<Any?>, Any?>
        return typed.handle(command)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(command: Command<R>): CommandResult<R> {
        return chain(command as Command<Any?>) as CommandResult<R>
    }
}
