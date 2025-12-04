Kommand
======

A tiny, dependency‑light Command Bus for Kotlin/JVM. It helps you model application behavior as Commands handled by dedicated Handlers, with optional Middleware
to intercept, log, validate, or transform execution. Results are returned in a simple wrapper so your application code stays explicit and testable.

What this library is for

- Encapsulate actions as Command objects with a single handler each
- Centralize dispatching through a CommandBus
- Compose cross‑cutting concerns using Middleware (e.g., logging, metrics, tracing, validation)

Highlights

- Minimal surface: Command, CommandHandler, CommandBus (with SimpleCommandBus implementation), CommandMiddleware, DomainEvent, CommandResult
- Optional: DomainEventPublisher, MessageOutboxRepository, OutboxMiddleware, OutboxPublisher for the outbox pattern
- EventDispatcher to route published domain events to one or many handlers
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

4) Create the bus and execute (suspend)

```kotlin
val bus = SimpleCommandBus(
    handlers = mapOf(
        // SimpleCommandBus maps handlers by the command KClass (type)
        Greet::class to GreetHandler()
    ),
    middlewares = listOf(LoggingMiddleware())
)

// Execute is suspend; run from a coroutine
val result = kotlinx.coroutines.runBlocking { bus.execute(Greet("World")) }
println(result.result) // -> Ok(Hello, World!)
println(result.events) // -> [Logged(name=LoggingMiddleware handled Greet)] if your middleware adds events
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

// 2) Build the bus with OutboxMiddleware to persist events returned by handlers
val outboxRepo = JdbcOutboxRepository()
val bus = SimpleCommandBus(
    handlers = mapOf(/* CommandKClass -> handler, e.g. CreateUser::class to CreateUserHandler() */),
    middlewares = listOf(OutboxMiddleware(outboxRepo))
)

// 3) Execute commands as usual; events returned by handlers are saved to the outbox
val res = kotlinx.coroutines.runBlocking { bus.execute(/* your command */) }

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

Build

- Using the Gradle wrapper: ./gradlew build (ensure the wrapper is executable on your system)
- Kotlin/JVM toolchain is pinned to Java 21 via Gradle toolchains; the Foojay resolver plugin helps locate/install matching JDKs automatically.

Why Kommand?

- Keep business logic explicit and decoupled
- Make cross‑cutting concerns composable with middleware
- Encourage testable, intention‑revealing code

Notes and design trade‑offs

- Handler lookup key: `SimpleCommandBus` uses the command type (`KClass`) as the key in its `handlers` map:
  `Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>>`.
- Execute return value: `CommandBus.execute` returns the full `CommandResult<R>` (containing a `Result<R, CommandError>` and `events`). This lets callers
  inspect success/error and emitted events explicitly.
- Events: Handlers return domain events alongside the result in `CommandResult.events`. Middleware can publish them to a bus of your choice or aggregate them
  for later processing.
