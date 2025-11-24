package com.ps

private typealias ChainFunction = (Command<*>) -> CommandResult<*>

class SimpleCommandBus(
    val handlers: Map<Command<*>, CommandHandler<out Command<*>, *>> = emptyMap(),
    middlewares: List<CommandMiddleware> = emptyList()
) : CommandBus {

    private val chain: ChainFunction

    init {
        chain = middlewares.foldRight(this::dispatch as ChainFunction) { mw, next ->
            wrap(mw, next)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun wrap(mw: CommandMiddleware, next: ChainFunction): ChainFunction = { cmd ->
        mw.invoke(
            cmd as Command<Any?>,
            next as (Command<Any?>) -> CommandResult<Any?>
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(command: Command<*>): CommandResult<*> {
        val handler = handlers[command]
            ?: error("No handler registered for command ${command::class}")
        val typedHandler = handler as CommandHandler<Command<*>, *>
        return typedHandler.handle(command)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> execute(command: Command<R>): R {
        val res = chain(command)
        return res.result as R
    }
}
