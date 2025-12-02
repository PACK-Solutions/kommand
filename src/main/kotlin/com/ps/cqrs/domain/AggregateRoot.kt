package com.ps.cqrs.domain

import com.ps.cqrs.domain.events.DomainEvent
import java.time.Instant

/**
 * Base abstract class for Aggregate Roots in the domain model.
 *
 * An **Aggregate Root** is a special type of Entity that serves as the entry point to an
 * aggregate - a cluster of domain objects that are treated as a single unit for data changes.
 * The aggregate root is responsible for maintaining the consistency boundary and coordinating
 * changes to all objects within the aggregate.
 *
 * ## Key Responsibilities:
 * 1. **Consistency Boundary**: Ensures all invariants within the aggregate are maintained
 * 2. **Event Management**: Records domain events that occur during business operations
 * 3. **Transactional Boundary**: All changes to the aggregate happen in a single transaction
 * 4. **External Access Point**: Only the root can be referenced from outside the aggregate
 *
 * ## Event Management:
 * Aggregate roots maintain an internal list of domain events that are recorded during
 * business operations. These events represent facts about what happened in the domain.
 *
 * ### Event Lifecycle:
 * 1. **Record**: During a business operation, call `recordEvent()` to add events
 * 2. **Retrieve**: After the operation, access `domainEvents` to get all recorded events
 * 3. **Clear**: After publishing events, call `clearEvents()` to reset the event list
 *
 * ## Why Separate Event Management?
 * Events are managed separately from the aggregate's state because:
 * - Events are transient and should not be persisted as part of the aggregate state
 * - Events need to be published after the aggregate is successfully saved
 * - Event clearing should happen after successful publishing
 * - This separation enables the Transactional Outbox pattern
 *
 * ## Thread Safety:
 * This implementation is NOT thread-safe. Aggregates should be accessed and modified
 * within a single transaction/coroutine context. If concurrent access is needed,
 * implement optimistic locking at the repository level.
 *
 * @param ID The type of the aggregate root's unique identifier
 */
@Suppress("TooManyFunctions")
abstract class AggregateRoot<ID> : Entity<ID>() {
    /**
     * Internal mutable list of domain events recorded during business operations.
     *
     * This list is private to prevent external modification. Events can only be added
     * via `recordEvent()` and cleared via `clearEvents()`.
     */
    private val _domainEvents: MutableList<DomainEvent> = mutableListOf()

    /**
     * Read-only view of all domain events recorded by this aggregate.
     *
     * This property exposes the events that have been recorded during business operations.
     * Events should be retrieved after the aggregate has been saved, then published via
     * a DomainEventPublisher (typically using the Transactional Outbox pattern).
     *
     * ## Usage Pattern:
     * ```kotlin
     * // After saving the aggregate
     * val events = aggregate.domainEvents
     * events.forEach { event ->
     *     eventPublisher.publish(event)
     * }
     * aggregate.clearEvents()
     * ```
     *
     * @return An immutable list of domain events
     */
    val domainEvents: List<DomainEvent>
        get() = _domainEvents.toList()

    /**
     * Records a domain event that occurred during a business operation.
     *
     * This method should be called from within domain methods whenever something
     * significant happens that domain experts care about. Events are stored internally
     * and can be retrieved via the `domainEvents` property.
     *
     * ## When to Record Events:
     * - After successfully completing a business operation
     * - When an aggregate transitions to a new state
     * - When an important business rule is satisfied
     * - When something happens that other parts of the system need to know about
     *
     * ## Example:
     * ```kotlin
     * fun placeOrder(items: List<OrderItem>) {
     *     require(items.isNotEmpty()) { "Order must have at least one item" }
     *
     *     this.items = items
     *     this.status = OrderStatus.PLACED
     *
     *     recordEvent(OrderPlacedEvent(
     *         aggregateId = id.value,
     *         orderId = id.value,
     *         items = items.map { it.toDto() },
     *         placedAt = Instant.now()
     *     ))
     * }
     * ```
     *
     * @param event The domain event to record
     */
    protected fun recordEvent(event: DomainEvent) {
        _domainEvents.add(event)
    }

    /**
     * Clears all recorded domain events.
     *
     * This method should be called AFTER events have been successfully published to
     * ensure they are not published multiple times. Typically called by the infrastructure
     * layer after the Transactional Outbox has stored the events.
     *
     * ## Usage Pattern:
     * ```kotlin
     * // 1. Save the aggregate
     * repository.save(aggregate)
     *
     * // 2. Publish events (via Outbox)
     * aggregate.domainEvents.forEach { event ->
     *     outboxRepository.save(OutboxMessage.from(event))
     * }
     *
     * // 3. Clear events to prevent re-publishing
     * aggregate.clearEvents()
     * ```
     *
     * ## Why Clear Events?
     * - Prevents duplicate event publishing if the aggregate is saved again
     * - Keeps the aggregate's memory footprint small
     * - Clearly separates the "recording" phase from the "publishing" phase
     */
    fun clearEvents() {
        _domainEvents.clear()
    }

    /**
     * Returns the number of domain events currently recorded.
     *
     * Useful for testing and debugging to verify that the expected number of
     * events were recorded during a business operation.
     *
     * @return The count of recorded events
     */
    fun eventCount(): Int = _domainEvents.size

    /**
     * Checks if there are any recorded domain events.
     *
     * @return `true` if there are recorded events, `false` otherwise
     */
    fun hasEvents(): Boolean = _domainEvents.isNotEmpty()

    /**
     * Timestamp when this aggregate was created.
     * Should be set by the infrastructure layer when first persisting the aggregate.
     */
    open var createdAt: Instant? = null
        protected set

    /**
     * Identifier of the user/system that created this aggregate.
     * Should be set by the infrastructure layer when first persisting the aggregate.
     */
    open var createdBy: String? = null
        protected set

    /**
     * Timestamp when this aggregate was last updated.
     * Should be updated by the infrastructure layer on every save operation.
     */
    open var updatedAt: Instant? = null
        protected set

    /**
     * Identifier of the user/system that last updated this aggregate.
     * Should be updated by the infrastructure layer on every save operation.
     */
    open var updatedBy: String? = null
        protected set

    /**
     * Timestamp when this aggregate was soft-deleted.
     * If null, the aggregate is not deleted. If set, the aggregate is considered deleted.
     */
    open var deletedAt: Instant? = null
        protected set

    /**
     * Identifier of the user/system that soft-deleted this aggregate.
     */
    open var deletedBy: String? = null
        protected set

    /**
     * Version number for optimistic locking.
     * Should be incremented by the infrastructure layer on every save operation.
     */
    open var version: Long = 0
        protected set

    /**
     * Marks this aggregate as created with audit information.
     * Should be called by the infrastructure layer when first persisting the aggregate.
     *
     * @param by Identifier of the user/system creating this aggregate
     * @param at Timestamp of creation (defaults to now)
     */
    internal fun markAsCreated(by: String, at: Instant = Instant.now()) {
        createdAt = at
        createdBy = by
        updatedAt = at
        updatedBy = by
    }

    /**
     * Marks this aggregate as updated with audit information.
     * Should be called by the infrastructure layer on every save operation after creation.
     *
     * @param by Identifier of the user/system updating this aggregate
     * @param at Timestamp of update (defaults to now)
     */
    internal fun markAsUpdated(by: String, at: Instant = Instant.now()) {
        updatedAt = at
        updatedBy = by
    }

    /**
     * Performs a soft delete on this aggregate.
     * The aggregate is marked as deleted but not physically removed from storage.
     *
     * @param by Identifier of the user/system deleting this aggregate
     * @param at Timestamp of deletion (defaults to now)
     */
    fun softDelete(by: String, at: Instant = Instant.now()) {
        require(!isDeleted()) { "Aggregate is already deleted" }
        deletedAt = at
        deletedBy = by
        updatedAt = at
        updatedBy = by
    }

    /**
     * Restores a soft-deleted aggregate.
     *
     * @param by Identifier of the user/system restoring this aggregate
     * @param at Timestamp of restoration (defaults to now)
     */
    fun restore(by: String, at: Instant = Instant.now()) {
        require(isDeleted()) { "Aggregate is not deleted" }
        deletedAt = null
        deletedBy = null
        updatedAt = at
        updatedBy = by
    }

    /**
     * Checks if this aggregate has been soft-deleted.
     *
     * @return `true` if the aggregate is deleted, `false` otherwise
     */
    fun isDeleted(): Boolean = deletedAt != null

    /**
     * Checks if this aggregate is active (not deleted).
     *
     * @return `true` if the aggregate is active, `false` otherwise
     */
    fun isActive(): Boolean = deletedAt == null

    /**
     * Increments the version number for optimistic locking.
     * Should be called by the infrastructure layer on every save operation.
     */
    internal fun incrementVersion() {
        version++
    }
}
