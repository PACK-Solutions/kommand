package com.ps.cqrs.query

/**
 * Interface for query handlers that process queries and retrieve data.
 *
 * A **QueryHandler** is responsible for executing a specific query and returning the requested data.
 * Unlike command handlers, query handlers focus solely on data retrieval and never modify system state.
 *
 * ## Key Responsibilities:
 * 1. **Retrieve** data from repositories or read models
 * 2. **Transform** domain entities into DTOs (Data Transfer Objects)
 * 3. **Optimize** queries for read performance
 * 4. **Never modify** system state (read-only operations)
 *
 * ## Example:
 * ```kotlin
 * class GetUserByIdQueryHandler(
 *     private val userRepository: UserRepository
 * ) : QueryHandler<GetUserByIdQuery, UserDto?> {
 *
 *     override suspend fun invoke(query: GetUserByIdQuery): UserDto? {
 *         val user = userRepository.findById(query.userId)
 *         return user?.let { UserDto.from(it) }
 *     }
 * }
 *
 * // Usage: handler(query) instead of handler.handle(query)
 * val user = getUserHandler(GetUserByIdQuery("user-123"))
 * ```
 *
 * @param Q The type of query this handler processes
 * @param R The type of result returned by the query
 */
interface QueryHandler<in Q : Query, out R> {

    /**
     * Handles the given query and returns the result.
     * This operator function allows calling the handler as: `handler(query)`
     *
     * @param query The query to handle
     * @return The result of the query
     */
    suspend operator fun invoke(query: Q): R
}
