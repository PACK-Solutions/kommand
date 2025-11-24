package com.ps

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleCommandBusTest {

    // Command to deposit funds into a bank account
    data class DepositFunds(
        val accountId: String,
        val amountCents: Long,
        val actorId: String,
        val actorRoles: Set<String>,
    ) : Command<Unit>

    // Domain events
    data class DepositInitiated(val accountId: String, val amountCents: Long, val actorId: String) : DomainEvent
    data class FundsDeposited(val accountId: String, val amountCents: Long) : DomainEvent
    data class PermissionDeniedEvent(val actorId: String, val missingRole: String) : DomainEvent
    data class Logged(val message: String) : DomainEvent

    // Errors
    data class PermissionDenied(val reason: String) : CommandError

    // Simple handler that "deposits" funds and emits events.
    class DepositFundsHandler : CommandHandler<DepositFunds, Unit> {
        override fun handle(command: DepositFunds): CommandResult<Unit> {
            require(command.amountCents > 0) { "amount must be positive" }
            val events = listOf(
                DepositInitiated(command.accountId, command.amountCents, command.actorId),
                FundsDeposited(command.accountId, command.amountCents),
            )
            return CommandResult(Ok(Unit), events)
        }
    }

    // Middleware: permission checker. Requires either 'teller' or 'admin' role to proceed.
    class PermissionMiddleware(
        private val requiredAnyOf: Set<String> = setOf("teller", "admin"),
    ) : CommandMiddleware {
        override fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R> {
            return when (command) {
                is DepositFunds -> {
                    val hasRole = command.actorRoles.any { it in requiredAnyOf }
                    if (!hasRole) {
                        // Short-circuit with an error result and a domain event noting the denial
                        val missing = requiredAnyOf.first()
                        CommandResult(
                            Err(PermissionDenied("actor ${command.actorId} lacks role $missing")),
                            listOf(PermissionDeniedEvent(command.actorId, missing)),
                        )
                    } else {
                        next(command)
                    }
                }

                else -> {
                    next(command)
                }
            }
        }
    }

    // Middleware: a logger that records messages and appends a Logged event after execution
    class LoggerMiddleware(private val name: String, private val logs: MutableList<String>) : CommandMiddleware {
        override fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R> {
            logs.add("$name:before ${command::class.simpleName}")
            val res = next(command)
            logs.add("$name:after ${command::class.simpleName}")
            val newEvents = res.events + Logged("$name handled ${command::class.simpleName}")
            return CommandResult(res.result, newEvents)
        }
    }

    @Test
    fun `banking - successful deposit with permission and logging`() {
        // Arrange
        val logs = mutableListOf<String>()
        val logger = LoggerMiddleware("logger1", logs)
        val perms = PermissionMiddleware() // requires teller or admin

        val cmd = DepositFunds(
            accountId = "ACC-123",
            amountCents = 25_00,
            actorId = "user-42",
            actorRoles = setOf("teller"),
        )

        val capturedEventSets = mutableListOf<List<DomainEvent>>()
        val capture = object : CommandMiddleware {
            override fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R> {
                val res = next(command)
                capturedEventSets.add(res.events)
                return res
            }
        }

        val bus = SimpleCommandBus(
            handlers = mapOf(cmd to DepositFundsHandler()),
            middlewares = listOf(capture, logger, perms),
        )

        // Act
        val returned = bus.execute(cmd)

        // Assert logs order (foldRight => capture wraps logger wraps perms wraps handler)
        assertEquals(
            listOf(
                "logger1:before DepositFunds",
                "logger1:after DepositFunds",
            ),
            logs,
        )

        // Assert final event set observed by capture
        val events = capturedEventSets.single()
        assertEquals(
            listOf(
                DepositInitiated("ACC-123", 25_00, "user-42"),
                FundsDeposited("ACC-123", 25_00),
                Logged("logger1 handled DepositFunds"),
            ),
            events,
        )

        // Assert the returned CommandResult mirrors the events and is Ok
        assertTrue(returned.result.isOk)
        assertEquals(events, returned.events)
    }

    @Test
    fun `banking - deposit denied without proper role`() {
        // Arrange
        val logs = mutableListOf<String>()
        val logger = LoggerMiddleware("logger1", logs)
        val perms = PermissionMiddleware() // requires teller or admin

        val cmd = DepositFunds(
            accountId = "ACC-XYZ",
            amountCents = 10_00,
            actorId = "user-7",
            actorRoles = setOf("customer"),
        )

        // Capture final event set
        val capturedEventSets = mutableListOf<List<DomainEvent>>()
        val capture = object : CommandMiddleware {
            override fun <R> invoke(command: Command<R>, next: (Command<R>) -> CommandResult<R>): CommandResult<R> {
                val res = next(command)
                capturedEventSets.add(res.events)
                return res
            }
        }

        val bus = SimpleCommandBus(
            handlers = mapOf(cmd to DepositFundsHandler()),
            middlewares = listOf(capture, logger, perms),
        )

        // Act
        val returned = bus.execute(cmd)

        // Assert: permission middleware short-circuits the handler, but since the logger wraps it,
        // the logger still logs both before and after around the short-circuited call.
        assertEquals(listOf("logger1:before DepositFunds", "logger1:after DepositFunds"), logs)

        // Final events contain the permission denial event and the logger's Logged event, no handler events
        val events = capturedEventSets.single()
        assertEquals(
            listOf(
                PermissionDeniedEvent("user-7", "teller"),
                Logged("logger1 handled DepositFunds"),
            ),
            events,
        )

        // Sanity: there should be no FundsDeposited event
        assertTrue(events.none { it is FundsDeposited })

        // Assert returned result is Err with the expected error and events mirror captured
        assertTrue(returned.result.isErr)
        assertEquals(events, returned.events)
    }
}
