package com.ps.cqrs.query

import kotlin.reflect.KClass

/**
 * Minimal [QueryBus] implementation that composes a middleware chain and
 * dispatches queries to handlers.
 *
 * Notes:
 * - Handler lookup is performed using the exact KClass of the concrete [Query].
 *   Register your handlers keyed by the query class (e.g. `GetUserById::class`).
 * - Middlewares are composed so the first middleware in the list becomes the
 *   outermost wrapper (runs before and after the rest). This is implemented by
 *   iterating the list in reverse at initialization time.
 *
 * Example
 * ```kotlin
 * data class GetUserById(val id: String) : Query
 * data class UserDto(val id: String, val email: String)
 *
 * class GetUserByIdHandler : QueryHandler<GetUserById, UserDto?> {
 *     override suspend fun invoke(query: GetUserById): UserDto? =
 *         UserDto(query.id, "a@b.com")
 * }
 *
 * val bus: QueryBus = SimpleQueryBus(
 *     handlers = mapOf(GetUserById::class to GetUserByIdHandler()),
 *     middlewares = listOf(/* QueryMiddleware instances */)
 * )
 *
 * // inside a coroutine scope
 * val dto: UserDto? = bus.execute(GetUserById("u-1"))
 * ```
 */
class SimpleQueryBus(
    private val handlers: Map<KClass<out Query>, QueryHandler<*, *>> = emptyMap(),
    middlewares: List<QueryMiddleware> = emptyList(),
) : QueryBus {

    private val chain: suspend (Query) -> Any?

    init {
        var next: suspend (Query) -> Any? = { q -> dispatch(q) }
        for (mw in middlewares.asReversed()) {
            val current = next
            next = { q -> mw.invoke(q, current) }
        }
        chain = next
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun dispatch(query: Query): Any? {
        val handler = handlers[query::class]
            ?: error(missingHandlerMessage(query::class))
        val typed = handler as QueryHandler<Query, Any?>
        return typed.invoke(query)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(query: Query): R = chain(query) as R

    private fun missingHandlerMessage(kclass: KClass<*>): String {
        val known = if (handlers.isEmpty()) "<none>" else handlers.keys.joinToString { it.simpleName ?: it.toString() }
        return "No handler registered for query $kclass. Registered query types: $known. " +
            "Ensure you registered the handler keyed by the query KClass or use QueryBusBuilder DSL."
    }
}
