package com.ps.cqrs.query

/**
 * Executes [Query] objects via their registered handlers, optionally passing through middlewares.
 *
 * Implementations locate the appropriate [QueryHandler] and compose any [QueryMiddleware]
 * to run before/after the handler. Handler lookup is by the exact [kotlin.reflect.KClass]
 * of the query instance.
 *
 * Example
 * ```kotlin
 * // Define a query and handler
 * data class GetUserById(val id: String) : Query
 *
 * class GetUserByIdHandler : QueryHandler<GetUserById, UserDto?> {
 *     override suspend fun invoke(query: GetUserById): UserDto? {
 *         return UserDto(query.id, "a@b.com", "Ada")
 *     }
 * }
 *
 * // Minimal bus using SimpleQueryBus
 * val bus: QueryBus = SimpleQueryBus(
 *     handlers = mapOf(GetUserById::class to GetUserByIdHandler())
 * )
 *
 * // Execute
 * val dto: UserDto? = bus.execute(GetUserById("u-1"))
 * ```
 */
interface QueryBus {
    suspend fun <R> execute(query: Query): R
}
