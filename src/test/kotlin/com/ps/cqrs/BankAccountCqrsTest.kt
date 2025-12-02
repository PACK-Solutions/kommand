package com.ps.cqrs

import com.ps.cqrs.domain.events.BaseDomainEvent
import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.events.DomainEventHandler
import com.ps.cqrs.events.DomainEventPublisher
import com.ps.cqrs.events.EventDispatcher
import com.ps.cqrs.testfixtures.Account
import com.ps.cqrs.testfixtures.AccountId
import com.ps.cqrs.testfixtures.AccountOpened
import com.ps.cqrs.testfixtures.AccountReadModel
import com.ps.cqrs.testfixtures.DepositHandler
import com.ps.cqrs.testfixtures.DepositMoney
import com.ps.cqrs.testfixtures.GetAccountBalanceQuery
import com.ps.cqrs.testfixtures.GetAccountBalanceQueryHandler
import com.ps.cqrs.testfixtures.InMemoryOutbox
import com.ps.cqrs.testfixtures.MoneyDeposited
import com.ps.cqrs.testfixtures.MoneyWithdrawn
import com.ps.cqrs.testfixtures.OpenAccount
import com.ps.cqrs.testfixtures.OpenAccountHandler
import com.ps.cqrs.testfixtures.OverdraftRejected
import com.ps.cqrs.testfixtures.WithdrawHandler
import com.ps.cqrs.testfixtures.WithdrawMoney
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankAccountCqrsTest {

    @Test
    fun `commands update balance and emit expected events`() = runBlocking {
        val scenario = runScenario()

        assertEquals(1, scenario.r1.events.size)
        assertTrue(scenario.r1.events.first() is AccountOpened)
        assertTrue(scenario.r2.events.single() is MoneyDeposited)
        assertTrue(scenario.r3.events.single() is MoneyWithdrawn)
        assertTrue(scenario.r4.events.single() is OverdraftRejected)
        assertEquals(80, scenario.account.balance)
    }

    @Test
    fun `events carry base metadata and unique ids`() = runBlocking {
        val scenario = runScenario()
        val all = scenario.allEvents()
        assertBaseEventMetadata(all, scenario.accountId, scenario.testStart)
        // ids unique
        assertEquals(all.size, all.map { it.eventId }.toSet().size)
    }

    @Test
    fun `outbox stores all emitted events`() = runBlocking {
        val scenario = runScenario()
        val pending = scenario.outbox.findUnpublished()
        assertEquals(4, pending.size)
        pending.forEach { msg ->
            assertTrue(msg.event is BaseDomainEvent)
            assertEquals(scenario.accountId.value, msg.event.aggregateId)
        }
    }

    @Test
    fun `publishing moves events and preserves metadata`() = runBlocking {
        val scenario = runScenario()
        val published = mutableListOf<DomainEvent>()
        val publisher = DomainEventPublisher { event -> published += event }
        OutboxPublisher(scenario.outbox, publisher).publishPendingEvents()

        assertEquals(4, published.size)
        published.forEach { ev ->
            assertTrue(ev is BaseDomainEvent)
            assertEquals(scenario.accountId.value, ev.aggregateId)
        }
    }

    @Test
    fun `projection updates read model and answers query`() = runBlocking {
        val scenario = runScenario()
        val published = mutableListOf<DomainEvent>()
        val publisher = DomainEventPublisher { event -> published += event }
        OutboxPublisher(scenario.outbox, publisher).publishPendingEvents()

        val readModel = AccountReadModel()
        val dispatcher = EventDispatcher()
        registerDomainEventHandlers(dispatcher, readModel)
        published.forEach { event -> dispatcher.dispatch(event) }

        val queryHandler = GetAccountBalanceQueryHandler(readModel)
        val balance = queryHandler(GetAccountBalanceQuery(scenario.accountId))
        assertEquals(80, balance)
    }
}

// --- helpers ---
private data class Scenario(
    val accountId: AccountId,
    val account: Account,
    val outbox: InMemoryOutbox,
    val r1: com.ps.cqrs.command.CommandResult<Unit>,
    val r2: com.ps.cqrs.command.CommandResult<Long>,
    val r3: com.ps.cqrs.command.CommandResult<Long>,
    val r4: com.ps.cqrs.command.CommandResult<Long>,
    val testStart: java.time.Instant,
)

private suspend fun runScenario(): Scenario {
    val accountId = AccountId("acc-1")
    val account = Account(accountId)
    val testStart = java.time.Instant.now()
    val outbox = InMemoryOutbox()
    val bus = SimpleCommandBus(
        handlers = mapOf(
            OpenAccount::class to OpenAccountHandler(account),
            DepositMoney::class to DepositHandler(account),
            WithdrawMoney::class to WithdrawHandler(account),
        ),
        middlewares = listOf(OutboxMiddleware(outbox))
    )

    val r1 = bus.execute(OpenAccount(accountId, initial = 100))
    val r2 = bus.execute(DepositMoney(accountId, amount = 50))
    val r3 = bus.execute(WithdrawMoney(accountId, amount = 70))
    val r4 = bus.execute(WithdrawMoney(accountId, amount = 1000))

    return Scenario(accountId, account, outbox, r1, r2, r3, r4, testStart)
}

private fun Scenario.allEvents(): List<BaseDomainEvent> =
    (r1.events + r2.events + r3.events + r4.events).map { it as BaseDomainEvent }

private fun assertBaseEventMetadata(
    events: List<BaseDomainEvent>,
    accountId: AccountId,
    testStart: java.time.Instant,
) {
    val now = java.time.Instant.now()
    events.forEach { ev ->
        assertEquals(accountId.value, ev.aggregateId)
        assertTrue(ev.eventId.isNotBlank())
        assertTrue(!ev.occurredAt.isBefore(testStart))
        assertTrue(!ev.occurredAt.isAfter(now))
    }
}

private fun registerDomainEventHandlers(
    dispatcher: EventDispatcher,
    readModel: AccountReadModel,
) {
    dispatcher.register(
        AccountOpened::class,
        object : DomainEventHandler<AccountOpened> {
            override suspend fun handle(event: AccountOpened) {
                readModel.apply(event)
            }
        },
    )
    dispatcher.register(
        MoneyDeposited::class,
        object : DomainEventHandler<MoneyDeposited> {
            override suspend fun handle(event: MoneyDeposited) {
                readModel.apply(event)
            }
        },
    )
    dispatcher.register(
        MoneyWithdrawn::class,
        object : DomainEventHandler<MoneyWithdrawn> {
            override suspend fun handle(event: MoneyWithdrawn) {
                readModel.apply(event)
            }
        },
    )
    dispatcher.register(
        OverdraftRejected::class,
        object : DomainEventHandler<OverdraftRejected> {
            override suspend fun handle(event: OverdraftRejected) {
                readModel.apply(event)
            }
        },
    )
}
