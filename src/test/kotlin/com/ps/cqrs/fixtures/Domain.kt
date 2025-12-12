package com.ps.cqrs.fixtures

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ps.cqrs.command.CommandError
import com.ps.cqrs.domain.AggregateRoot

// ---- Domain primitives ----
data class AccountId(val value: String)

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
