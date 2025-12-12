package com.ps.cqrs.fixtures

import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.query.Query
import com.ps.cqrs.query.QueryHandler
import com.ps.cqrs.query.ReadModel

// ---- Read model + Query ----
class AccountReadModel : ReadModel {
    private val balances = mutableMapOf<String, Long>()

    override fun apply(event: DomainEvent) {
        when (event) {
            is AccountOpened -> balances[event.aggregateId] = event.initialBalance
            is MoneyDeposited -> balances[event.aggregateId] = event.newBalance
            is MoneyWithdrawn -> balances[event.aggregateId] = event.newBalance
            is OverdraftRejected -> Unit
            else -> Unit
        }
    }

    fun balance(id: AccountId): Long = balances[id.value] ?: 0
}

data class GetAccountBalanceQuery(val id: AccountId) : Query

class GetAccountBalanceQueryHandler(private val readModel: AccountReadModel) :
    QueryHandler<GetAccountBalanceQuery, Long> {
    override suspend fun invoke(query: GetAccountBalanceQuery): Long = readModel.balance(query.id)
}
