### Gaps and correctness risks
- Missing transaction boundary for atomicity
  - As implemented, `OutboxMiddleware` writes events after the handler, but there’s nothing ensuring it occurs in the same database transaction as the handler’s state changes. A failure between domain state persistence and outbox write could break the outbox guarantee.
  - Recommended: Add a `TransactionMiddleware` that wraps both the handler and `OutboxMiddleware`. Also make outbox repositories reuse the ambient transaction/connection. See “Enhancement 1” below.
- Handler wiring safety
  - Handlers are looked up from `Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>>` with unchecked casts at dispatch time. Mis-registrations become runtime errors.
- Query-side symmetry
  - There is no `QueryBus`. Handlers are called directly in tests. That’s fine, but you lose middleware features (caching, metrics, auth) and uniform wiring.
- Operational aspects
  - `OutboxPublisher` has basic failure handling but lacks backoff, idempotency guarantees, and observability hooks.
- Concurrency and duplicates
  - If multiple publisher instances run, you’ll want a locking/claiming mechanism to avoid duplicate work; the interface doesn’t expose that yet.

### Proposed enhancements

#### 1) Transactional boundary and outbox atomicity
- Introduce `TransactionManager` and `TransactionMiddleware` (outermost) so that handler work and `OutboxMiddleware` writes are committed/rolled back together.
- Let `MessageOutboxRepository` implementations reuse the current transaction (e.g., via coroutine context) and open a new connection when none is present (e.g., in `OutboxPublisher`).
- Middleware order should be: `[TransactionMiddleware, OutboxMiddleware, ...]`.

Benefits: Enforces the transactional outbox pattern: either state changes and outbox writes both succeed, or both roll back.

#### 2) Type-safe handler registration to avoid wiring mistakes
- Provide a builder/DSL that enforces type alignment at compile time and hides unsafe casts:

```kotlin
class CommandBusBuilder {
    private val map = mutableMapOf<KClass<*>, CommandHandler<*, *>>()

    inline fun <reified C : Command<R>, R> handle(handler: CommandHandler<C, R>) {
        map[C::class] = handler
    }

    fun build(middlewares: List<CommandMiddleware> = emptyList()): SimpleCommandBus =
        SimpleCommandBus(handlers = map as Map<KClass<out Command<*>>, CommandHandler<out Command<*>, *>>, middlewares = middlewares)
}

// Usage
val bus = CommandBusBuilder().apply {
    handle(OpenAccountHandler(account))
    handle(DepositHandler(account))
    handle(WithdrawHandler(account))
}.build(middlewares = listOf(TransactionMiddleware(tx), OutboxMiddleware(outbox)))
```

- Alternatively, expose a typed registration function on `SimpleCommandBus` companion:

```kotlin
object Buses {
    inline fun buildCommandBus(configure: CommandBusBuilder.() -> Unit): CommandBus =
        CommandBusBuilder().apply(configure).build()
}
```

#### 3) Add a `QueryBus` with middlewares
- Mirror the command bus for queries:
  - `QueryBus.execute(query)` with middleware chain (caching, metrics, auth, tracing) and typed registration DSL similar to commands.
  - This keeps read concerns separated yet configurable and makes it harder to miswire handlers.

#### 4) Improve `SimpleCommandBus` diagnostics and safety
- On missing handler, include helpful diagnostic: list registered command types and suggest how to register.
- Optionally guard against polymorphic pitfalls by resolving by exact class and documenting that subclasses must be registered explicitly, or add support for supertypes with a deterministic resolution strategy.
- Consider exposing `register` API to prevent constructing with partially wired maps in dynamic setups.

#### 5) Outbox repository and publisher robustness
- Idempotency
  - Persist a deterministic `eventId`/`messageId` unique constraint; ensure publisher is idempotent (safe to retry publish operations). If the external sink supports idempotency keys, pass `eventId` along.
- Claiming/locking
  - Add `claimUnpublished(batchSize, ownerId, ttl)` to atomically claim messages and avoid multi-consumer duplicates. Or use `SELECT ... FOR UPDATE SKIP LOCKED` semantics.
- Backoff and DLQ
  - Track `retryCount`, `nextAttemptAt`, and move messages to a dead-letter table/flag after max retries. Implement exponential backoff.
- Observability
  - Add hooks or a middleware around `OutboxPublisher` for metrics (success/failure counters, lag, retry histogram) and logging. Consider structured logs with message IDs and event types.
- Transactions in publisher
  - Wrap `markAsPublished`/`incrementRetryCount` in short transactions (per message or per batch). If you add claiming, perform claim and update within transactions.

#### 6) Event dispatching and projections
- Provide an `EventDispatcher` abstraction in the library for read model projections with:
  - Handler registration by event type, plus a typed DSL similar to commands.
  - Optional middleware for projections (e.g., outbox->inbox, idempotency against read-store updates, retries).
- Document the pattern of projecting events to read models and then answering queries from those models (your tests already demonstrate this—codify it in examples).

#### 7) Structured result and error handling
- Ensure `CommandResult` clearly models success/failure, validation errors, and domain errors. Consider:
  - A sealed result type (`Ok`, `Invalid`, `Error`) and keep `events` only on success, or always available as a list with clear semantics.
  - Add helpers to merge/append events in middlewares safely.

#### 8) Middleware ergonomics and ordering safety
- Provide a `MiddlewareStack` builder with named slots to reduce ordering mistakes:

```kotlin
class MiddlewareStack {
    private val list = mutableListOf<CommandMiddleware>()
    fun transactions(tm: TransactionManager) = apply { list += TransactionMiddleware(tm) }
    fun outbox(repo: MessageOutboxRepository) = apply { list += OutboxMiddleware(repo) }
    fun custom(mw: CommandMiddleware) = apply { list += mw }
    fun build(): List<CommandMiddleware> = list.toList()
}

val middlewares = MiddlewareStack()
    .transactions(txManager)
    .outbox(outbox)
    .build()
```

- Add a runtime check that `OutboxMiddleware` is present only once and (optionally) that it appears after `TransactionMiddleware` if one is present. Fail fast on misconfiguration.

#### 9) Threading/coroutines and resource management
- All interfaces are `suspend`, which is good. Document expectations for repository implementations (non-blocking drivers preferred, but if blocking JDBC is used, dispatch to dedicated IO dispatcher).
- If you add a transaction context via coroutine elements, document that all repositories must respect the ambient `TxContext`.

#### 10) Testing and examples
- Add a “wiring test” that builds the bus using your DSL and asserts all commands in a module have handlers registered (reflection-based or generated list).
- Add an integration-style test that simulates handler state persistence + outbox write inside a transaction to prove atomicity.
- Provide sample `JdbcOutboxRepository` to show how to reuse the ambient transaction connection.

### Minor nits and docs
- `SimpleCommandBus` kdoc mentions `foldRight`, but implementation uses `asReversed()` loop. The behavior is correct (first in list is outermost), but align the comment or mention the exact construction used.
- Expand KDocs to clearly state handler lookup is by exact `KClass` of the command instance (no polymorphic lookup). Add a recommendation for sealed commands or explicit registration if inheritance is used.

### Suggested reference architectures
- Minimal/core (what you have): Keep framework-free abstractions and provide examples for JDBC + coroutine context transactions.
- Spring variant: `TransactionMiddleware` backed by `TransactionTemplate`, repositories using `JdbcTemplate`/JPA reuse the same transaction automatically.
- Generator-backed variant: Use KSP to generate `CommandRegistry` and `QueryRegistry` to remove manual maps.

### Summary checklist
- Add `TransactionMiddleware` and transaction-aware outbox repo implementation.
- Provide a type-safe registration DSL to remove unsafe casts and wiring mistakes.
- Add a `QueryBus` with middlewares (caching, metrics) for symmetry.
- Harden outbox and publisher: claiming/locking, idempotency keys, backoff/DLQ, metrics.
- Improve diagnostics: better errors on missing handlers and middleware-order checks.
- Align docs/comments and add wiring/integration tests to guard configuration.

These changes preserve your clean design while substantially reducing misconfiguration risk and improving operational robustness in production setups.
