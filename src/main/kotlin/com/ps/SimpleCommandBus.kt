package com.ps

/**
 * Minimal [CommandBus] implementation that composes a middleware chain and
 * dispatches commands to handlers.
 *
 * Important notes:
 * - Handler lookup is performed using the concrete [Command] instance as a key in [handlers].
 *   This keeps the implementation tiny but also means you must register the exact command instance you plan to execute.
 * - Middlewares are composed outer-to-inner using `foldRight`, so the first middleware
 *   in the list becomes the outermost wrapper (runs before and after the rest).
 * - The [execute] method returns the full [CommandResult] from the middleware chain/handler,
 *   allowing callers to inspect both the success/error and any emitted events.
 *
 * @property handlers mapping used to resolve the handler for a given command instance
 * @param middlewares the ordered list of middlewares to wrap around dispatch
 */
class SimpleCommandBus(
    val handlers: Map<Command<*>, CommandHandler<out Command<*>, *>> = emptyMap(),
    middlewares: List<CommandMiddleware> = emptyList(),
) : CommandBus {

    private val chain: (Command<Any?>) -> CommandResult<Any?>

    init {
        chain = middlewares
            .foldRight({ cmd -> dispatch(cmd) })
            { mw, next ->
                { cmd -> mw.invoke(cmd, next) }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(command: Command<Any?>): CommandResult<Any?> {
        val handler = handlers[command] ?: error("No handler registered for command ${command::class}")
        val typed = handler as CommandHandler<Command<Any?>, Any?>
        return typed.handle(command)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> execute(command: Command<R>): CommandResult<R> {
        return chain(command as Command<Any?>) as CommandResult<R>
    }
}
