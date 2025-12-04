package com.ps.cqrs.command

import com.ps.cqrs.middleware.CommandMiddleware
import kotlin.reflect.KClass

/**
 * Type-safe builder to register command handlers without unchecked casts.
 *
 * Example
 * ```kotlin
 * // Define your commands and handlers
 * data class CreateUser(val email: String) : Command<UserId>
 * class CreateUserHandler : CommandHandler<CreateUser, UserId> {
 *     override suspend fun handle(command: CreateUser) =
 *         CommandResult(result = Ok(UserId("u-1")), events = emptyList())
 * }
 *
 * // Build a bus via the builder DSL
 * val bus = CommandBusBuilder().apply {
 *     handle(CreateUserHandler())
 * }.build(middlewares = listOf(OutboxMiddleware(/* repo */ object: MessageOutboxRepository {
 *     override suspend fun save(event: com.ps.cqrs.domain.events.DomainEvent) = MessageId("1")
 *     override suspend fun findUnpublished(limit: Int) = emptyList<OutboxMessage>()
 *     override suspend fun markAsPublished(id: MessageId) {}
 *     override suspend fun incrementRetryCount(id: MessageId) {}
 * })))
 *
 * // Execute inside a coroutine
 * val result: CommandResult<UserId> = bus.execute(CreateUser("a@b.com"))
 * ```
 */
class CommandBusBuilder {
    @PublishedApi
    internal val map = mutableMapOf<KClass<*>, CommandHandler<*, *>>()

    inline fun <reified C : Command<R>, R> handle(handler: CommandHandler<C, R>) {
        map[C::class] = handler
    }

    @Suppress("UNCHECKED_CAST")
    fun build(middlewares: List<CommandMiddleware> = emptyList()): CommandBus =
        SimpleCommandBus(
            handlers = map as Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>>,
            middlewares = middlewares,
        )
}

/**
 * Convenience helpers to build command buses using the [CommandBusBuilder] DSL.
 *
 * Example
 * ```kotlin
 * val bus = CommandBusDsl.buildCommandBus(
 *     middlewares = listOf(TransactionMiddleware(NoopTransactionManager))
 * ) {
 *     handle(CreateUserHandler())
 *     // handle(AnotherHandler())
 * }
 * ```
 */
object CommandBusDsl {
    inline fun buildCommandBus(
        middlewares: List<CommandMiddleware> = emptyList(),
        configure: CommandBusBuilder.() -> Unit,
    ): CommandBus = CommandBusBuilder().apply(configure).build(middlewares)
}
