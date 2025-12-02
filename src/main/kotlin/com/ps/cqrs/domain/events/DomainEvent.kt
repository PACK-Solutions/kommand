package com.ps.cqrs.domain.events

import java.time.Instant

/**
 * Base interface for all domain events.
 *
 * A **Domain Event** represents something that happened in the domain that domain experts
 * care about. Events are immutable facts about the past and are named in the past tense
 * (e.g., `UserRegistered`, `OrderPlaced`, `PaymentCompleted`).
 *
 * ## Key Characteristics:
 * - **Immutable**: Events represent facts that have already occurred and cannot be changed
 * - **Past tense naming**: Event names should describe what happened (e.g., `Created`, `Updated`, `Deleted`)
 * - **Rich in domain language**: Events should use the ubiquitous language of the domain
 * - **Self-contained**: Events should contain all the information needed to understand what happened
 *
 * ## Event Metadata:
 * All domain events must include:
 * - **eventId**: A unique identifier for this specific event occurrence
 * - **occurredAt**: The timestamp when the event occurred
 * - **aggregateId**: The ID of the aggregate that produced this event (for traceability)
 *
 * ## Usage Example:
 * ```kotlin
 * data class UserRegisteredEvent(
 *     override val eventId: String = UUID.randomUUID().toString(),
 *     override val occurredAt: Instant = Instant.now(),
 *     override val aggregateId: String,
 *     val userId: String,
 *     val email: String,
 *     val registeredAt: Instant
 * ) : DomainEvent
 *
 * data class OrderPlacedEvent(
 *     override val eventId: String = UUID.randomUUID().toString(),
 *     override val occurredAt: Instant = Instant.now(),
 *     override val aggregateId: String,
 *     val orderId: String,
 *     val customerId: String,
 *     val totalAmount: BigDecimal
 * ) : DomainEvent
 * ```
 *
 * ## Event Sourcing:
 * While this library doesn't enforce Event Sourcing, domain events can be used as the
 * foundation for event-sourced systems where the state of aggregates is reconstructed
 * from a sequence of events.
 *
 * ## Event Publishing:
 * Events are typically:
 * 1. Recorded by the AggregateRoot during domain operations
 * 2. Retrieved after the operation completes
 * 3. Published via a DomainEventPublisher (usually through the Transactional Outbox pattern)
 * 4. Consumed by event handlers for side effects (notifications, projections, integration)
 */
public interface DomainEvent {
    /**
     * A unique identifier for this specific event occurrence.
     *
     * This ID is used to:
     * - Ensure idempotent event processing (detect duplicates)
     * - Track event processing in logs and monitoring
     * - Correlate events across different systems
     *
     * Typically a UUID or similar globally unique identifier.
     */
    public val eventId: String

    /**
     * The timestamp when this event occurred.
     *
     * This represents the moment in time when the domain operation that produced
     * this event was completed. It's crucial for:
     * - Event ordering and sequencing
     * - Temporal queries and analytics
     * - Debugging and auditing
     *
     * Should be set when the event is created, typically using `Instant.now()`.
     */
    public val occurredAt: Instant

    /**
     * The identifier of the aggregate that produced this event.
     *
     * This links the event back to the aggregate instance that generated it,
     * enabling:
     * - Event stream reconstruction for a specific aggregate
     * - Traceability and debugging
     * - Aggregate-level event processing
     *
     * For example, if a `User` aggregate with ID "user-123" registers,
     * the `UserRegisteredEvent` would have `aggregateId = "user-123"`.
     */
    public val aggregateId: String
}
