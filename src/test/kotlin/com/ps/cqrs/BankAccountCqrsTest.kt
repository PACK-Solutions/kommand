package com.ps.cqrs

import com.ps.cqrs.domain.events.BaseDomainEvent
import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.events.DomainEventHandler
import com.ps.cqrs.events.DomainEventPublisher
import com.ps.cqrs.events.EventDispatcher
import com.ps.cqrs.fixtures.*
import com.ps.cqrs.mediator.Mediator
import com.ps.cqrs.mediator.MediatorDsl
import com.ps.cqrs.middleware.EventDispatchingMiddleware
import com.ps.cqrs.middleware.OutboxMiddleware
import com.ps.cqrs.middleware.TransactionMiddleware
import com.ps.cqrs.outbox.OutboxPublisher
import com.ps.cqrs.transaction.NoopTransactionManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BankAccountCqrsTest : FunSpec(
    {

        test("commands update balance and emit expected events") {
            val scenario = runScenario()

            scenario.r1.events shouldHaveSize 1
            scenario.r1.events.first().shouldBeInstanceOf<AccountOpened>()
            scenario.r2.events.single().shouldBeInstanceOf<MoneyDeposited>()
            scenario.r3.events.single().shouldBeInstanceOf<MoneyWithdrawn>()
            scenario.r4.events.single().shouldBeInstanceOf<OverdraftRejected>()
            scenario.account.balance shouldBe 80
        }

        test("events carry base metadata and unique ids") {
            val scenario = runScenario()
            val all = scenario.allEvents()
            assertBaseEventMetadata(all, scenario.accountId, scenario.testStart)
            // ids unique
            all.size shouldBe all.map { it.eventId }.toSet().size
        }

        test("outbox stores all emitted events") {
            val scenario = runScenario()
            val pending = scenario.outbox.findUnpublished()
            pending.size shouldBe 4
            pending.forEach { msg ->
                (msg.event is BaseDomainEvent).shouldBeTrue()
                msg.event.aggregateId shouldBe scenario.accountId.value
            }
        }

        test("publishing moves events and preserves metadata") {
            val scenario = runScenario()
            val published = mutableListOf<DomainEvent>()
            val publisher = DomainEventPublisher { event -> published += event }
            OutboxPublisher(scenario.outbox, publisher).publishPendingEvents()

            published.size shouldBe 4
            published.forEach { ev ->
                (ev is BaseDomainEvent).shouldBeTrue()
                ev.aggregateId shouldBe scenario.accountId.value
            }
        }

        test("projection updates read model and answers query") {
            val scenario = runScenario()
            val published = mutableListOf<DomainEvent>()
            val publisher = DomainEventPublisher { event -> published += event }
            OutboxPublisher(scenario.outbox, publisher).publishPendingEvents()

            val readModel = AccountReadModel()
            val dispatcher = EventDispatcher()
            registerDomainEventHandlers(dispatcher, readModel)
            published.forEach { event -> dispatcher.dispatch(event) }

            val mediator = MediatorDsl.buildMediator {
                handle(GetAccountBalanceQueryHandler(readModel))
            }
            val balance: Long = mediator.ask(GetAccountBalanceQuery(scenario.accountId))
            balance shouldBe 80
        }

        test("synchronous projection via middleware updates read model immediately") {
            val accountId = AccountId("acc-sync-1")
            val account = Account(accountId)
            val outbox = InMemoryOutbox()

            val readModel = AccountReadModel()
            val dispatcher = EventDispatcher()
            registerDomainEventHandlers(dispatcher, readModel)

            val mediator = MediatorDsl.buildMediator(
                commandMiddlewares = listOf(
                    TransactionMiddleware(NoopTransactionManager),
                    OutboxMiddleware(outbox),
                    EventDispatchingMiddleware(dispatcher),
                ),
            ) {
                handle(OpenAccountHandler(account))
                handle(DepositHandler(account))
                handle(WithdrawHandler(account))

                handle(GetAccountBalanceQueryHandler(readModel))
            }

            mediator.send(OpenAccount(accountId, initial = 100))
            mediator.send(DepositMoney(accountId, amount = 50))
            mediator.send(WithdrawMoney(accountId, amount = 70))

            val balance: Long = mediator.ask(GetAccountBalanceQuery(accountId))
            balance shouldBe 80
        }
    },
)

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
    val mediator: Mediator = MediatorDsl.buildMediator(
        commandMiddlewares = listOf(
            TransactionMiddleware(NoopTransactionManager),
            OutboxMiddleware(outbox),
        ),
    ) {
        handle(OpenAccountHandler(account))
        handle(DepositHandler(account))
        handle(WithdrawHandler(account))
    }

    val r1 = mediator.send(OpenAccount(accountId, initial = 100))
    val r2 = mediator.send(DepositMoney(accountId, amount = 50))
    val r3 = mediator.send(WithdrawMoney(accountId, amount = 70))
    val r4 = mediator.send(WithdrawMoney(accountId, amount = 1000))

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
        ev.aggregateId.shouldBe(accountId.value)
        (ev.eventId.isNotBlank()).shouldBe(true)
        (!ev.occurredAt.isBefore(testStart)).shouldBe(true)
        (!ev.occurredAt.isAfter(now)).shouldBe(true)
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
