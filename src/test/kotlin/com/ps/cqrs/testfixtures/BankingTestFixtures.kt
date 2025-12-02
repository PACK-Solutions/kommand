package com.ps.cqrs.testfixtures

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ps.cqrs.MessageId
import com.ps.cqrs.MessageOutboxRepository
import com.ps.cqrs.OutboxMessage
import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandError
import com.ps.cqrs.command.CommandHandler
import com.ps.cqrs.command.CommandResult
import com.ps.cqrs.domain.AggregateRoot
import com.ps.cqrs.domain.events.BaseDomainEvent
import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.query.Query
import com.ps.cqrs.query.QueryHandler

// ---- Domain primitives ----
data class AccountId(val value: String)

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

// ---- Errors ----
data class ValidationError(val message: String) : CommandError

// ---- Aggregate ----
class Account(override val id: AccountId) : AggregateRoot<AccountId>() {
    var balance: Long = 0
        private set

    fun open(initialBalance: Long): Result<Unit, CommandError> {
        if (initialBalance < 0) return Err(ValidationError("initial balance cannot be negative"))
        balance = initialBalance
        recordEvent(AccountOpened(id.value, initialBalance))
        return Ok(Unit)
    }

    fun deposit(amount: Long): Result<Long, CommandError> {
        if (amount <= 0) return Err(ValidationError("amount must be > 0"))
        balance += amount
        recordEvent(MoneyDeposited(id.value, amount, balance))
        return Ok(balance)
    }

    fun withdraw(amount: Long): Result<Long, CommandError> {
        return when {
            amount <= 0 -> Err(ValidationError("amount must be > 0"))
            amount > balance -> {
                recordEvent(OverdraftRejected(id.value, amount, balance))
                Err(ValidationError("insufficient funds"))
            }
            else -> {
                balance -= amount
                recordEvent(MoneyWithdrawn(id.value, amount, balance))
                Ok(balance)
            }
        }
    }
}

// ---- Commands ----
data class OpenAccount(val id: AccountId, val initial: Long) : Command<Unit>
data class DepositMoney(val id: AccountId, val amount: Long) : Command<Long>
data class WithdrawMoney(val id: AccountId, val amount: Long) : Command<Long>

// ---- Command Handlers ----
class OpenAccountHandler(private val account: Account) : CommandHandler<OpenAccount, Unit> {
    override suspend fun handle(command: OpenAccount): CommandResult<Unit> {
        val res = account.open(command.initial)
        val events = account.domainEvents
        account.clearEvents()
        return CommandResult(result = res, events = events)
    }
}

class DepositHandler(private val account: Account) : CommandHandler<DepositMoney, Long> {
    override suspend fun handle(command: DepositMoney): CommandResult<Long> {
        val res = account.deposit(command.amount)
        val events = account.domainEvents
        account.clearEvents()
        return CommandResult(result = res, events = events)
    }
}

class WithdrawHandler(private val account: Account) : CommandHandler<WithdrawMoney, Long> {
    override suspend fun handle(command: WithdrawMoney): CommandResult<Long> {
        val res = account.withdraw(command.amount)
        val events = account.domainEvents
        account.clearEvents()
        return CommandResult(result = res, events = events)
    }
}

// ---- Read model + Query ----
class AccountReadModel {
    private val balances = mutableMapOf<String, Long>()

    fun apply(event: DomainEvent) {
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

// ---- In-memory Outbox for tests ----
class InMemoryOutbox : MessageOutboxRepository {
    private val messages = mutableListOf<OutboxMessage>()

    override suspend fun save(event: DomainEvent): MessageId {
        val id = MessageId("m-${messages.size + 1}")
        messages += OutboxMessage(id = id, event = event)
        return id
    }

    override suspend fun findUnpublished(limit: Int): List<OutboxMessage> = messages.take(limit)
    override suspend fun markAsPublished(id: MessageId) { /* test impl */ }
    override suspend fun incrementRetryCount(id: MessageId) { /* test impl */ }
}
