package com.ps.cqrs.domain.events

import java.time.Instant
import java.util.UUID

/**
 * Abstract base class for domain events with enhanced metadata.
 *
 * This class provides a convenient base implementation of [DomainEvent] with additional
 * metadata fields that are useful for event tracking, correlation, and versioning.
 *
 * ## Additional Metadata:
 * - **aggregateType**: The type of aggregate that produced this event
 * - **eventVersion**: Schema version for event evolution
 * - **causationId**: ID of the command/event that caused this event
 * - **correlationId**: ID for tracking related events across aggregates
 *
 * ## Usage Example:
 * ```kotlin
 * data class UserRegisteredEvent(
 *     override val aggregateId: String,
 *     val userId: String,
 *     val email: String,
 *     val registeredAt: Instant,
 *     override val causationId: String? = null,
 *     override val correlationId: String? = null
 * ) : BaseDomainEvent(
 *     aggregateId = aggregateId,
 *     aggregateType = "User",
 *     eventVersion = 1,
 *     causationId = causationId,
 *     correlationId = correlationId
 * )
 * ```
 *
 * ## Event Versioning:
 * The `eventVersion` field enables event schema evolution:
 * ```kotlin
 * // Version 1
 * data class UserRegisteredEventV1(
 *     override val aggregateId: String,
 *     val email: String
 * ) : BaseDomainEvent(aggregateId, "User", eventVersion = 1)
 *
 * // Version 2 (added name field)
 * data class UserRegisteredEventV2(
 *     override val aggregateId: String,
 *     val email: String,
 *     val name: String
 * ) : BaseDomainEvent(aggregateId, "User", eventVersion = 2)
 * ```
 *
 * ## Event Correlation:
 * Use `causationId` and `correlationId` to track event chains:
 * ```kotlin
 * // Command causes event
 * val command = RegisterUserCommand(commandId = "cmd-123")
 * val event = UserRegisteredEvent(
 *     causationId = command.commandId,  // What caused this event
 *     correlationId = command.correlationId  // Business transaction ID
 * )
 *
 * // Event causes another event
 * val followUpEvent = WelcomeEmailSentEvent(
 *     causationId = event.eventId,  // Caused by UserRegisteredEvent
 *     correlationId = event.correlationId  // Same business transaction
 * )
 * ```
 *
 * @param aggregateId The ID of the aggregate that produced this event
 * @param aggregateType The type name of the aggregate (e.g., "User", "Order")
 * @param eventVersion The schema version of this event (default: 1)
 * @param eventId Unique identifier for this event occurrence (auto-generated)
 * @param occurredAt Timestamp when the event occurred (auto-generated)
 * @param causationId ID of the command/event that caused this event (optional)
 * @param correlationId ID for tracking related events across aggregates (optional)
 */
@Suppress("LongParameterList")
abstract class BaseDomainEvent(
    override val aggregateId: String,
    val aggregateType: String,
    val eventVersion: Int = 1,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now(),
    val causationId: String? = null,
    val correlationId: String? = null
) : DomainEvent {

    /**
     * Returns the simple name of this event class.
     *
     * This is useful for logging and event routing.
     */
    fun eventType(): String = this::class.simpleName ?: "UnknownEvent"

    override fun toString(): String {
        return "${eventType()}(eventId=$eventId, aggregateId=$aggregateId, occurredAt=$occurredAt)"
    }
}
