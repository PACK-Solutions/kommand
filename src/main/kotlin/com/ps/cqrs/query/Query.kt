package com.ps.cqrs.query

/**
 * Marker interface for all queries in the application layer.
 *
 * A **Query** represents a request to retrieve data from the system without modifying its state.
 * Queries are part of the **Command/Query Separation (CQS)** pattern, where queries handle reads
 * (data retrieval) and commands handle writes (state changes).
 *
 * ## Key Characteristics:
 * - **Question naming**: Queries are named as questions or data requests (e.g., `GetUserById`, `FindActiveOrders`)
 * - **Read-only**: Queries never modify system state
 * - **Return data**: Queries return data structures (DTOs, projections, view models)
 * - **Immutable**: Queries should be immutable data structures
 * - **Optimized for reading**: Can bypass a domain model for performance
 *
 * ## Query vs Command:
 * - **Query**: Reads data, returns data, never changes state, named as questions
 * - **Command**: Changes state, may return a simple result (ID, success/failure), named as verbs
 *
 * ## Naming Convention:
 * Queries should be named to express the data being requested:
 * - `GetUserByIdQuery`
 * - `FindActiveOrdersQuery`
 * - `ListProductsByCategoryQuery`
 * - `SearchCustomersQuery`
 * - `GetOrderSummaryQuery`
 *
 * ## Usage Example:
 * ```kotlin
 * data class GetUserByIdQuery(
 *     val userId: String
 * ) : Query {
 *     init {
 *         require(userId.isNotBlank()) { "User ID cannot be blank" }
 *     }
 * }
 *
 * data class FindActiveOrdersQuery(
 *     val customerId: String,
 *     val page: Int = 0,
 *     val pageSize: Int = 20
 * ) : Query {
 *     init {
 *         require(page >= 0) { "Page must be non-negative" }
 *         require(pageSize > 0) { "Page size must be positive" }
 *     }
 * }
 *
 * data class SearchProductsQuery(
 *     val searchTerm: String,
 *     val category: String? = null,
 *     val minPrice: BigDecimal? = null,
 *     val maxPrice: BigDecimal? = null,
 *     val sortBy: String = "name",
 *     val sortDirection: SortDirection = SortDirection.ASC
 * ) : Query
 * ```
 *
 * ## Query Structure:
 * Queries are typically implemented as Kotlin data classes containing:
 * 1. **Identifiers**: IDs of entities to retrieve
 * 2. **Filters**: Criteria for filtering results
 * 3. **Pagination**: Page number, page size, offset
 * 4. **Sorting**: Sort field and direction
 * 5. **Projections**: Which fields to include/exclude
 *
 * ## Query Flow:
 * ```
 * ┌──────────────┐      ┌─────────────┐      ┌──────────────────┐
 * │ Presentation │─────→│ QueryHandler│─────→│ Query Repository │
 * │   Layer      │      │             │      │ or Read Model    │
 * └──────────────┘      └─────────────┘      └──────────────────┘
 *                                                      │
 *                                                      ↓
 *                                             ┌─────────────────┐
 *                                             │ Database        │
 *                                             │ (Optimized)     │
 *                                             └─────────────────┘
 * ```
 *
 * ## CQRS: Separate Read and Write Models:
 * In CQRS (Command Query Responsibility Segregation), queries can use a separate
 * read model optimized for querying:
 *
 * - **Write Model** (Commands):
 *   - Normalized domain model
 *   - Enforces business rules
 *   - Optimized for consistency
 *
 * - **Read Model** (Queries):
 *   - Denormalized views
 *   - Optimized for specific queries
 *   - Can use different storage (e.g., Elasticsearch, Redis)
 *   - Updated via domain events
 *
 * ## Query Optimization Strategies:
 *
 * ### 1. Direct Database Queries:
 * Bypass the domain model for read-only operations:
 * ```kotlin
 * class GetUserByIdQueryHandler(
 *     private val jdbcTemplate: JdbcTemplate
 * ) : QueryHandler<GetUserByIdQuery, UserDto?> {
 *     override suspend fun handle(query: GetUserByIdQuery): UserDto? {
 *         return jdbcTemplate.queryForObject(
 *             "SELECT id, email, name FROM users WHERE id = ?",
 *             UserDto::class.java,
 *             query.userId
 *         )
 *     }
 * }
 * ```
 *
 * ### 2. Projections:
 * Return only the data needed by the client:
 * ```kotlin
 * data class GetUserProfileQuery(
 *     val userId: String,
 *     val projection: UserProjection = UserProjection.SUMMARY
 * ) : Query
 *
 * enum class UserProjection {
 *     SUMMARY,    // id, name, email
 *     DETAILED,   // + address, phone, preferences
 *     FULL        // + all related entities
 * }
 * ```
 *
 * ### 3. Caching:
 * Cache frequently accessed query results:
 * ```kotlin
 * class CachedGetUserByIdQueryHandler(
 *     private val delegate: QueryHandler<GetUserByIdQuery, UserDto?>,
 *     private val cache: Cache<String, UserDto>
 * ) : QueryHandler<GetUserByIdQuery, UserDto?> {
 *     override suspend fun handle(query: GetUserByIdQuery): UserDto? {
 *         return cache.get(query.userId) ?: delegate.handle(query)?.also {
 *             cache.put(query.userId, it)
 *         }
 *     }
 * }
 * ```
 *
 * ## Return Types:
 * Queries typically return:
 * - **DTOs** (Data Transfer Objects): Simple data structures for API responses
 * - **View Models**: UI-specific data structures
 * - **Projections**: Partial views of entities
 * - **Collections**: Lists, pages, or streams of data
 *
 * Example return types:
 * ```kotlin
 * data class UserDto(
 *     val id: String,
 *     val email: String,
 *     val name: String,
 *     val createdAt: Instant
 * )
 *
 * data class PagedResult<T>(
 *     val items: List<T>,
 *     val totalCount: Long,
 *     val page: Int,
 *     val pageSize: Int
 * ) {
 *     val totalPages: Int = (totalCount / pageSize).toInt() + if (totalCount % pageSize > 0) 1 else 0
 *     val hasNext: Boolean = page < totalPages - 1
 *     val hasPrevious: Boolean = page > 0
 * }
 * ```
 *
 * ## Query Handler:
 * Each query is processed by a corresponding QueryHandler:
 * ```kotlin
 * class GetUserByIdQueryHandler(
 *     private val userRepository: UserQueryRepository
 * ) : QueryHandler<GetUserByIdQuery, UserDto?> {
 *     override suspend fun handle(query: GetUserByIdQuery): UserDto? {
 *         return userRepository.findById(query.userId)
 *     }
 * }
 * ```
 *
 * ## Testing:
 * Queries are easy to test because they are simple data structures:
 * ```kotlin
 * @Test
 * fun `should reject query with blank user ID`() {
 *     assertThrows<IllegalArgumentException> {
 *         GetUserByIdQuery(userId = "")
 *     }
 * }
 *
 * @Test
 * fun `should retrieve user by ID`() = runTest {
 *     val query = GetUserByIdQuery(userId = "user-123")
 *     val result = queryHandler.handle(query)
 *
 *     assertNotNull(result)
 *     assertEquals("user-123", result.id)
 * }
 * ```
 */
interface Query
