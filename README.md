Kommand
======

A tiny, dependency‑light Mediator for Kotlin/JVM. It helps you model application behavior as Commands and Queries handled by dedicated Handlers, with optional
Middleware
to intercept, log, validate, or transform execution. Results are returned explicitly so your application code stays clear and testable.

What this library is for

- Encapsulate actions as Command objects with a single handler each, and reads as Query objects
- Centralize dispatching through a Mediator
- Compose cross‑cutting concerns using Middleware (e.g., logging, metrics, tracing, validation) for commands and queries

Highlights

- Minimal surface: Command, CommandHandler, Mediator, CommandMiddleware, DomainEvent, CommandResult, Query, QueryHandler, QueryMiddleware
- Optional: DomainEventPublisher, MessageOutboxRepository, OutboxMiddleware, OutboxPublisher for the outbox pattern
- EventDispatchingMiddleware for in-process, synchronous projections via EventDispatcher
- Straightforward generics for typed results
- No reflection, no magic — just Kotlin

Domain modeling helpers: Entity, ValueObject, AggregateRoot

Kommand includes a few tiny building blocks to help you keep your domain model clear and consistent. They are intentionally minimal and unopinionated so you can
adopt only what you need.

- ValueObject (`src/main/kotlin/com/ps/cqrs/domain/ValueObject.kt`)
    - Marker interface for immutable value types defined entirely by their attributes
    - Prefer data classes that implement `ValueObject`

  Example:

  ```kotlin
  // A simple immutable value object
  data class Money(val amount: Long, val currency: String) : ValueObject
  ```

- Entity<ID> (`src/main/kotlin/com/ps/cqrs/domain/Entity.kt`)
    - Base abstract class for identity-based domain objects
    - Equality and hash code are based on the `id` only

  Example:

  ```kotlin
  // Entity identified by a value object ID
  data class UserId(val value: String) : ValueObject

  abstract class User : Entity<UserId>() {
      abstract override val id: UserId
  }
  ```

- AggregateRoot<ID> (`src/main/kotlin/com/ps/cqrs/domain/AggregateRoot.kt`)
    - Special entity that acts as the consistency boundary for a cluster of objects
    - Records domain events during operations and exposes them via `domainEvents`
    - Provides helpers such as `recordEvent`, `clearEvents`, `hasEvents`, and basic audit fields (`createdAt`, `updatedAt`, soft delete, etc.)

  Example (simplified):

  ```kotlin
  data class OrderId(val value: String) : ValueObject

  data class OrderPlaced(val aggregateId: String) : DomainEvent

  class Order(override val id: OrderId) : AggregateRoot<OrderId>() {
      fun place() {
          // domain invariants here...
          recordEvent(OrderPlaced(aggregateId = id.value))
      }
  }

  // After saving the aggregate in your repository
  val events = order.domainEvents
  events.forEach { publisher.publish(it) }
  order.clearEvents()
  ```

Tech stack

- Language: Kotlin (JVM)
- Build: Gradle (Wrapper configured)
- Static analysis: Detekt (with formatting rules)
- Utilities: kotlin-result (for ergonomic success/error handling)
- Toolchain: Java 21 (configured via Gradle toolchains; Foojay resolver plugin)

Getting started

1) Add dependency
   This project is currently set up as a Gradle Kotlin project. If you consume it as a module in a multi‑project build, add it as a dependency in your app
   module:

```kotlin
dependencies {
    implementation(project(":kommand"))
}
```

If you publish it to your internal repository, the coordinates are based on the current build file:
group: com.ps
name: kommand
version: 1.0-SNAPSHOT

2) Define your Command and Handler (suspend)

```kotlin
// A command returning a String
data class Greet(val name: String) : Command<String>

class GreetHandler : CommandHandler<Greet, String> {
    override suspend fun handle(command: Greet): CommandResult<String> =
        CommandResult(Ok("Hello, ${command.name}!"))
}
```

3) Optionally add Middleware (suspend)

```kotlin
class LoggingMiddleware : CommandMiddleware {
    override suspend fun <R> invoke(
        command: Command<R>,
        next: suspend (Command<R>) -> CommandResult<R>
    ): CommandResult<R> {
        println("Executing command: $command")
        return next(command)
    }
}
```

4) Build a mediator and execute (suspend)

```kotlin
val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
    commandMiddlewares = listOf(LoggingMiddleware())
) {
    handle(GreetHandler())
}

// Execute is suspend; run from a coroutine
val result = kotlinx.coroutines.runBlocking { mediator.send(Greet("World")) }
println(result.result) // -> Ok(Hello, World!)
println(result.events) // -> events your handler/middleware produced
```

Outbox pattern and event publishing

Kommand encourages handlers to return domain events alongside their result. You can then persist those events to an outbox within the same transaction and
publish them asynchronously.

Key types provided:

- `DomainEventPublisher`: your adapter to publish a single `DomainEvent` to Kafka, SNS, etc.
- `MessageOutboxRepository` and `OutboxMessage`: abstraction to persist and fetch pending events.
- `OutboxMiddleware`: middleware that saves emitted events to the outbox automatically.
- `OutboxPublisher`: a pull-based publisher that reads pending outbox messages and calls your `DomainEventPublisher`.

Example wiring (suspend):

```kotlin
// 1) Define your repository + publisher adapters in your application module
class JdbcOutboxRepository(/* datasource, etc. */) : MessageOutboxRepository { /* ... */ }
class KafkaEventPublisher(/* kafka client */) : DomainEventPublisher {
    override suspend fun publish(event: DomainEvent) { /* serialize + send */
    }
}

// 2) Build the mediator with OutboxMiddleware to persist events returned by handlers
val outboxRepo = JdbcOutboxRepository()
val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator(
    commandMiddlewares = listOf(com.ps.cqrs.middleware.OutboxMiddleware(outboxRepo))
) {
    handle(/* e.g. CreateUserHandler() */)
}

// 3) Execute commands as usual; events returned by handlers are saved to the outbox
val res = kotlinx.coroutines.runBlocking { mediator.send(/* your command */) }

// 4) In a separate scheduler/worker, publish pending events (suspend)
val publisher = OutboxPublisher(outboxRepo, KafkaEventPublisher())
kotlinx.coroutines.runBlocking { publisher.publishPendingEvents(batchSize = 100) }
```

Projecting events to read models

After your outbox publisher sends events to the outside world, you can route them to projection/update handlers using the provided `EventDispatcher`.

```kotlin
val readModel = object {
    fun apply(event: DomainEvent) {}
} // your projection
val dispatcher = EventDispatcher()

dispatcher.register(UserCreatedEvent::class, object : DomainEventHandler<UserCreatedEvent> {
    override suspend fun handle(event: UserCreatedEvent) {
        readModel.apply(event)
    }
})

// Drive the dispatcher with the events you consumed from your broker
suspend fun project(consumedEvents: List<DomainEvent>) {
    consumedEvents.forEach { event -> dispatcher.dispatch(event) }
}
```

Note: If you prefer injecting an outbox repository or publisher directly into each handler, you can do so. Handlers can save or publish their own events;
however, using `OutboxMiddleware` keeps handlers focused on pure business logic while ensuring events are captured consistently.

Synchronous vs eventual projections (when can a query read the updated model?)

Kommand supports two projection strategies. Choose based on your consistency and coupling needs:

- Eventual projections (default):
    - Configure OutboxMiddleware to persist emitted domain events atomically with your writes.
    - Publish outbox events asynchronously (e.g., with OutboxPublisher) and project them using EventDispatcher in your projection worker.
    - Your query/read model is updated eventually. A query executed immediately after `mediator.send(command)` may not reflect the new state yet.

- Synchronous in-process projections:
    - Add EventDispatchingMiddleware(dispatcher) to the command pipeline and register your DomainEventHandler projections with the same EventDispatcher.
    - Place middlewares in this order: TransactionMiddleware -> OutboxMiddleware -> EventDispatchingMiddleware.
    - With this setup, projections (e.g., read models) are updated during the same call to `send`. A query asked right after `send` can read the updated model.

Example (synchronous projection):

```kotlin
val readModel = AccountReadModel()
val dispatcher = EventDispatcher()

// Register projection handlers
dispatcher.register(AccountOpened::class, object : DomainEventHandler<AccountOpened> {
    override suspend fun handle(event: AccountOpened) {
        readModel.apply(event)
    }
})

val mediator = MediatorDsl.buildMediator(
    commandMiddlewares = listOf(
        TransactionMiddleware(NoopTransactionManager),
        OutboxMiddleware(outboxRepo),
        EventDispatchingMiddleware(dispatcher), // must come after OutboxMiddleware
    )
) {
    handle(OpenAccountHandler(domainAccount))
    handle(GetAccountBalanceQueryHandler(readModel))
}

runBlocking {
    mediator.send(OpenAccount(AccountId("acc-1"), initial = 100))
    val balance: Long = mediator.ask(GetAccountBalanceQuery(AccountId("acc-1")))
    println(balance) // reflects projection updated synchronously
}
```

Queries with the Mediator

Define a `Query` and a `QueryHandler`, then ask the mediator:

```kotlin
data class GetUserById(val id: String) : com.ps.cqrs.query.Query

class GetUserByIdHandler(/* repo, etc. */) : com.ps.cqrs.query.QueryHandler<GetUserById, UserDto?> {
    override suspend fun invoke(query: GetUserById): UserDto? = /* fetch */ null
}

val mediator = com.ps.cqrs.mediator.MediatorDsl.buildMediator {
    handle(GetUserByIdHandler())
}

val dto: UserDto? = kotlinx.coroutines.runBlocking { mediator.ask(GetUserById("u-1")) }
```

Mediator-only API

This library exposes the Mediator as the single public entry point. Legacy bus adapters and builders (`CommandBus`, `QueryBus`, and their DSLs) are now internal
and not part of the public API. Build a mediator and use `send` for commands and `ask` for queries as shown above.

Build

- Using the Gradle wrapper: ./gradlew build (ensure the wrapper is executable on your system)
- Kotlin/JVM toolchain is pinned to Java 21 via Gradle toolchains; the Foojay resolver plugin helps locate/install matching JDKs automatically.

Why Kommand?

- Keep business logic explicit and decoupled
- Make cross‑cutting concerns composable with middleware
- Encourage testable, intention‑revealing code

Notes and design trade‑offs

- Handler lookup key: the Mediator uses the request type (`KClass`) as the key in its internal maps, e.g.
  `Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>>` for commands and
  `Map<KClass<out Query>, QueryHandler<*, *>>` for queries.
- Return values: `mediator.send(command)` returns the full `CommandResult<R>` (containing a `Result<R, CommandError>` and `events`).
  `mediator.ask(query)` returns the query's value directly.
- Events: Handlers return domain events alongside the result in `CommandResult.events`. Middleware (such as `OutboxMiddleware`) can persist
  them for later publication, or you can publish them immediately in a custom middleware.
