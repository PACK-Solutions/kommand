package com.ps.cqrs

import com.ps.cqrs.domain.events.DomainEvent
import com.ps.cqrs.events.DomainEventPublisher
import com.ps.cqrs.fixtures.*
import com.ps.cqrs.mediator.MediatorDsl
import com.ps.cqrs.middleware.OutboxMiddleware
import com.ps.cqrs.middleware.TransactionMiddleware
import com.ps.cqrs.outbox.OutboxPublisher
import com.ps.cqrs.transaction.NoopTransactionManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DomainEventPublisherTest : FunSpec({

    test("forwards events from outbox to provided publisher in order") {
        val (outbox, expectedOrder) = prepareOutboxWithFourEvents()

        val received = mutableListOf<String>()
        val publisher = DomainEventPublisher { ev: DomainEvent ->
            received += ev.javaClass.simpleName
        }

        OutboxPublisher(outbox, publisher).publishPendingEvents()

        received.shouldContainExactly(expectedOrder)
    }

    test("continues publishing after a publisher error for one event") {
        val (outbox, expectedOrder) = prepareOutboxWithFourEvents()

        val received = mutableListOf<String>()
        var attempts = 0
        val publisher = DomainEventPublisher { ev: DomainEvent ->
            attempts += 1
            if (ev is MoneyDeposited) error("boom")
            print(ev.javaClass.simpleName)
            received += ev.javaClass.simpleName
        }

        OutboxPublisher(outbox, publisher).publishPendingEvents()

        // We attempted to publish all 4 events, but one failed and thus wasn't collected
        attempts shouldBe 4
        received.shouldContainExactly(listOf("AccountOpened", "MoneyWithdrawn", "OverdraftRejected"))
    }

    test("fan-out composition: a publisher can delegate to multiple sinks") {
        val (outbox, expectedOrder) = prepareOutboxWithFourEvents()

        val sinkA = mutableListOf<String>()
        val sinkB = mutableListOf<String>()

        val fanOut = DomainEventPublisher { ev ->
            val name = ev.javaClass.simpleName
            sinkA += name
            sinkB += name
        }

        OutboxPublisher(outbox, fanOut).publishPendingEvents()

        sinkA.shouldContainExactly(expectedOrder)
        sinkB.shouldContainExactly(expectedOrder)
        sinkA shouldHaveSize 4
        sinkB shouldHaveSize 4
    }
})

// --- helpers ---
private suspend fun prepareOutboxWithFourEvents(): Pair<InMemoryOutbox, List<String>> {
    val accountId = AccountId("acc-pub-1")
    val account = Account(accountId)
    val outbox = InMemoryOutbox()

    val mediator = MediatorDsl.buildMediator(
        commandMiddlewares = listOf(
            TransactionMiddleware(NoopTransactionManager),
            OutboxMiddleware(outbox),
        ),
    ) {
        handle(OpenAccountHandler(account))
        handle(DepositHandler(account))
        handle(WithdrawHandler(account))
    }

    mediator.send(OpenAccount(accountId, initial = 100))
    mediator.send(DepositMoney(accountId, amount = 50))
    mediator.send(WithdrawMoney(accountId, amount = 70))
    mediator.send(WithdrawMoney(accountId, amount = 1000))

    val expectedOrder = listOf(
        AccountOpened::class.java.simpleName,
        MoneyDeposited::class.java.simpleName,
        MoneyWithdrawn::class.java.simpleName,
        OverdraftRejected::class.java.simpleName,
    )

    return outbox to expectedOrder
}
