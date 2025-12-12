package com.ps.cqrs.fixtures

import com.ps.cqrs.domain.events.BaseDomainEvent

// ---- Events (using BaseDomainEvent) ----
data class AccountOpened(
    override val aggregateId: String,
    val initialBalance: Long,
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = "Account",
)

data class MoneyDeposited(
    override val aggregateId: String,
    val amount: Long,
    val newBalance: Long,
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = "Account",
)

data class MoneyWithdrawn(
    override val aggregateId: String,
    val amount: Long,
    val newBalance: Long,
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = "Account",
)

data class OverdraftRejected(
    override val aggregateId: String,
    val attemptedAmount: Long,
    val balance: Long,
) : BaseDomainEvent(
    aggregateId = aggregateId,
    aggregateType = "Account",
)
