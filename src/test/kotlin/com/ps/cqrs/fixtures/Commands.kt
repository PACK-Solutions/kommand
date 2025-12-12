package com.ps.cqrs.fixtures

import com.ps.cqrs.command.Command
import com.ps.cqrs.command.CommandHandler
import com.ps.cqrs.command.CommandResult

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
