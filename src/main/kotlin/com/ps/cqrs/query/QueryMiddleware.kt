package com.ps.cqrs.query

/**
 * Middleware that can observe, short-circuit, or augment query execution.
 *
 * Example
 * ```kotlin
 * // Basic caching middleware sketch
 * class CachingMiddleware(private val cache: MutableMap<Any, Any?> = mutableMapOf()) : QueryMiddleware {
 *     @Suppress("UNCHECKED_CAST")
 *     override suspend fun <R> invoke(query: Query, next: suspend (Query) -> R): R {
 *         val key = query
 *         if (cache.containsKey(key)) return cache[key] as R
 *         return next(query).also { cache[key] = it }
 *     }
 * }
 *
 * // Register with the mediator
 * val mediator = MediatorDsl.buildMediator(
 *     queryMiddlewares = listOf(CachingMiddleware())
 * ) {
 *     handle(/* YourQueryHandler() */)
 * }
 * ```
 */
interface QueryMiddleware {
    suspend fun <R> invoke(query: Query, next: suspend (Query) -> R): R
}
