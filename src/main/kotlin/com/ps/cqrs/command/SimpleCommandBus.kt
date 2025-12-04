package com.ps.cqrs.command

import com.ps.cqrs.middleware.CommandMiddleware
import com.ps.cqrs.middleware.OutboxMiddleware
import com.ps.cqrs.middleware.TransactionMiddleware
import kotlin.reflect.KClass

/**
 * Minimal [CommandBus] implementation that composes a middleware chain and
 * dispatches commands to handlers.
 *
 * Important notes:
 * - Handler lookup is performed using the [KClass] of the concrete [Command] in [handlers].
 *   Register your handlers keyed by the command class (e.g. `CreateTodo::class`).
 * - Middlewares are composed so the first middleware in the list becomes the outermost
 *   wrapper (runs before and after the rest). This is implemented by iterating the list
 *   in reverse at initialization time.
 * - The [execute] method returns the full [CommandResult] from the middleware chain/handler,
 *   allowing callers to inspect both the success/error and any emitted events.
 *
 * Handler lookup is by exact command class. If you use inheritance for commands, register
 * handlers for each concrete subtype explicitly.
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
 *             result = Ok(id),
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
        // Middleware configuration safety checks
        validateMiddlewareOrdering(middlewares)

        var next: suspend (Command<Any?>) -> CommandResult<Any?> = { cmd -> dispatch(cmd) }
        for (mw in middlewares.asReversed()) {
            val currentNext = next
            next = { cmd -> mw.invoke(cmd, currentNext) }
        }
        chain = next
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(command: Command<Any?>): CommandResult<Any?> {
        val handler = handlers[command::class] ?: error(missingHandlerMessage(command::class))
        val typed = handler as CommandHandler<Command<Any?>, Any?>
        return typed.handle(command)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(command: Command<R>): CommandResult<R> {
        return chain(command as Command<Any?>) as CommandResult<R>
    }

    private fun missingHandlerMessage(command: KClass<*>): String {
        val known = if (handlers.isEmpty()) "<none>" else handlers.keys.joinToString { it.simpleName ?: it.toString() }
        return "No handler registered for command $command. Registered command types: $known. " +
            "Ensure you registered the handler keyed by the command KClass or use CommandBusBuilder DSL."
    }

    private fun validateMiddlewareOrdering(middlewares: List<CommandMiddleware>) {
        // Ensure OutboxMiddleware appears at most once
        val outboxes = middlewares.filterIsInstance<OutboxMiddleware>()
        require(outboxes.size <= 1) {
            "OutboxMiddleware is registered ${outboxes.size} times. It must appear at most once."
        }
        // If TransactionMiddleware present, ensure it appears before OutboxMiddleware
        val txIndex = middlewares.indexOfFirst { it is TransactionMiddleware }
        val outboxIndex = middlewares.indexOfFirst { it is OutboxMiddleware }
        if (txIndex >= 0 && outboxIndex >= 0) {
            require(txIndex < outboxIndex) {
                "TransactionMiddleware must be placed before OutboxMiddleware to ensure outbox atomicity."
            }
        }
    }
}
