package com.ps.cqrs.query

import com.ps.cqrs.domain.events.DomainEvent

/**
 * Read models (a.k.a. projections) are the query-side view of your domain.
 * They subscribe to domain events and update a denormalized state that is
 * optimized for reads. A read model should be fast to query and cheap to
 * rebuild from the event stream when needed.
 *
 * Implementations are expected to be side-effect free beyond maintaining
 * their own state. The framework will call [apply] for every [DomainEvent]
 * in order, allowing the read model to project the latest state.
 *
 * Example
 * ```kotlin
 * // A simple read model that keeps account balances up to date
 * class AccountReadModel : ReadModel {
 *     private val balances = mutableMapOf<String, Long>()
 *
 *     override fun apply(event: DomainEvent) {
 *         when (event) {
 *             is AccountOpened -> balances[event.aggregateId] = event.initialBalance
 *             is MoneyDeposited -> balances[event.aggregateId] = event.newBalance
 *             is MoneyWithdrawn -> balances[event.aggregateId] = event.newBalance
 *             is OverdraftRejected -> Unit // no state change for rejected operations
 *             else -> Unit
 *         }
 *     }
 *
 *     fun balance(id: AccountId): Long = balances[id.value] ?: 0
 * }
 *
 * // A query and its handler using the read model
 * data class GetAccountBalanceQuery(val id: AccountId) : Query
 *
 * class GetAccountBalanceQueryHandler(private val readModel: AccountReadModel) :
 *     QueryHandler<GetAccountBalanceQuery, Long> {
 *     override suspend fun invoke(query: GetAccountBalanceQuery): Long =
 *         readModel.balance(query.id)
 * }
 * ```
 *
 * See test fixtures for a complete, runnable example:
 * - `src/test/kotlin/com/ps/cqrs/fixtures/Queries.kt`
 */
interface ReadModel {
    fun apply(event: DomainEvent)
}
