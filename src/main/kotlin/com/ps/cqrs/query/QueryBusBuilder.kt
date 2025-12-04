package com.ps.cqrs.query

import kotlin.reflect.KClass

/**
 * Type-safe builder to register query handlers without unchecked casts.
 *
 * Example
 * ```kotlin
 * // Define a query and handler
 * data class FindUser(val id: String) : Query
 * class FindUserHandler : QueryHandler<FindUser, UserDto?> {
 *     override suspend fun invoke(query: FindUser): UserDto? = UserDto(query.id, "a@b.com", "Ada")
 * }
 *
 * // Build a query bus via the builder DSL
 * val qbus = QueryBusBuilder().apply {
 *     handle(FindUserHandler())
 * }.build(middlewares = listOf(/* QueryMiddleware */))
 *
 * // Execute
 * val dto: UserDto? = qbus.execute(FindUser("u-1"))
 * ```
 */
class QueryBusBuilder {
    @PublishedApi
    internal val map = mutableMapOf<KClass<*>, QueryHandler<*, *>>()

    inline fun <reified Q : Query, R> handle(handler: QueryHandler<Q, R>) {
        map[Q::class] = handler
    }

    @Suppress("UNCHECKED_CAST")
    fun build(middlewares: List<QueryMiddleware> = emptyList()): QueryBus =
        SimpleQueryBus(
            handlers = map as Map<KClass<out Query>, QueryHandler<*, *>>,
            middlewares = middlewares,
        )
}

/**
 * Convenience helpers to build query buses using the [QueryBusBuilder] DSL.
 *
 * Example
 * ```kotlin
 * val qbus = QueryBusDsl.buildQueryBus { handle(FindUserHandler()) }
 * val user = qbus.execute(FindUser("u-1"))
 * ```
 */
object QueryBusDsl {
    inline fun buildQueryBus(
        middlewares: List<QueryMiddleware> = emptyList(),
        configure: QueryBusBuilder.() -> Unit,
    ): QueryBus = QueryBusBuilder().apply(configure).build(middlewares)
}
