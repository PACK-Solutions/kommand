package com.ps.cqrs

/**
 * Abstraction over a transaction boundary.
 *
 * Implementations should start/join a transaction, execute [block], and then
 * commit or roll back appropriately.
 *
 * Example
 * ```kotlin
 * // Pseudo JDBC-based implementation
 * class JdbcTransactionManager(private val dataSource: DataSource) : TransactionManager {
 *     override suspend fun <T> withinTransaction(block: suspend () -> T): T {
 *         val conn = dataSource.connection
 *         var ok = false
 *         return try {
 *             conn.autoCommit = false
 *             val result = block()
 *             conn.commit()
 *             ok = true
 *             result
 *         } finally {
 *             if (!ok) conn.rollback()
 *             conn.close()
 *         }
 *     }
 * }
 *
 * // Wiring with the CommandBus
 * val txManager = JdbcTransactionManager(ds)
 * val bus = SimpleCommandBus(
 *     handlers = mapOf(/* YourCommand::class to YourHandler() */),
 *     middlewares = listOf(TransactionMiddleware(txManager))
 * )
 * ```
 */
interface TransactionManager {
    suspend fun <T> withinTransaction(block: suspend () -> T): T
}

/**
 * Default no-op implementation that simply executes the block without any transaction.
 */
object NoopTransactionManager : TransactionManager {
    override suspend fun <T> withinTransaction(block: suspend () -> T): T = block()
}
