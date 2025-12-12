package com.ps.cqrs.events

import com.ps.cqrs.domain.events.DomainEvent

/**
 * Interface for handling domain events.
 *
 * A **DomainEventHandler** processes domain events to perform side effects such as:
 * - Updating read models (CQRS projections)
 * - Sending notifications (emails, push notifications)
 * - Integrating with external systems
 * - Triggering workflows or sagas
 * - Updating analytics or reporting databases
 *
 * ## Key Characteristics:
 * - **Asynchronous**: Handlers process events asynchronously after the main transaction
 * - **Idempotent**: Handlers should be idempotent (safe to process the same event multiple times)
 * - **Independent**: Each handler operates independently and doesn't affect the main transaction
 * - **Side Effects Only**: Handlers should not modify the write model (aggregates)
 *
 * ## Handler Pattern:
 * Each event type can have multiple handlers. This creates a one-to-many relationship:
 * - `UserRegisteredEvent` → `SendWelcomeEmailHandler`, `CreateUserProfileHandler`, `UpdateAnalyticsHandler`
 * - `OrderPlacedEvent` → `SendOrderConfirmationHandler`, `UpdateInventoryHandler`, `NotifyWarehouseHandler`
 *
 *
 * ## Usage Example:
 * ```kotlin
 * class SendWelcomeEmailHandler(
 *     private val emailService: EmailService,
 *     private val userRepository: UserRepository
 * ) : DomainEventHandler<UserRegisteredEvent> {
 *
 *     override suspend fun handle(event: UserRegisteredEvent) {
 *         val user = userRepository.findById(event.userId) ?: return
 *
 *         emailService.ask(
 *             to = user.email,
 *             subject = "Welcome!",
 *             body = "Welcome to our platform, ${user.name}!"
 *         )
 *     }
 * }
 * ```
 *
 * ## Event Dispatcher
 * Register handlers with an event dispatcher to route events to one or more handlers
 * by their concrete event type. This library provides a simple in-memory
 * [com.ps.cqrs.events.EventDispatcher].
 *
 * ```kotlin
 * val dispatcher = EventDispatcher()
 * dispatcher.register(UserRegisteredEvent::class, SendWelcomeEmailHandler(emailService))
 * dispatcher.register(UserRegisteredEvent::class, UpdateUserProjectionHandler(repo, processedRepo))
 *
 * // Later, when an event is published/consumed
 * suspend fun onEvent(event: UserRegisteredEvent) {
 *     dispatcher.dispatch(event)
 * }
 * ```
 *
 * ## Idempotency:
 * Handlers MUST be idempotent because events may be delivered multiple times:
 * ```kotlin
 * class UpdateUserProjectionHandler(
 *     private val projectionRepository: UserProjectionRepository,
 *     private val processedEventRepository: ProcessedEventRepository
 * ) : DomainEventHandler<UserRegisteredEvent> {
 *
 *     override suspend fun handle(event: UserRegisteredEvent) {
 *         // Check if already processed
 *         if (processedEventRepository.exists(event.eventId)) {
 *             return // Already processed, skip
 *         }
 *
 *         // Process the event
 *         projectionRepository.insert(UserProjection.from(event))
 *
 *         // Mark as processed
 *         processedEventRepository.save(event.eventId)
 *     }
 * }
 * ```
 *
 * ## Error Handling:
 * Handlers should handle errors gracefully:
 * ```kotlin
 * override suspend fun handle(event: UserRegisteredEvent) {
 *     try {
 *         emailService.ask(...)
 *     } catch (e: EmailServiceException) {
 *         // Log error but don't fail the entire event processing
 *         // logger.error("Failed to ask welcome email", e)
 *         // Optionally: Store in dead letter queue for retry
 *         // deadLetterQueue.add(event, e)
 *     }
 * }
 * ```
 *
 * ## Testing:
 * Event handlers are easy to test in isolation:
 * ```kotlin
 * @Test
 * fun `should ask welcome email when user registered`() = runTest {
 *     // Given
 *     val event = UserRegisteredEvent(
 *         userId = "user-123",
 *         email = "john@example.com",
 *         registeredAt = Instant.now()
 *     )
 *     val emailService = mockk<EmailService>()
 *     val handler = SendWelcomeEmailHandler(emailService, userRepository)
 *
 *     coEvery { emailService.ask(any(), any(), any()) } just Runs
 *
 *     // When
 *     handler.handle(event)
 *
 *     // Then
 *     coVerify {
 *         emailService.ask(
 *             to = "john@example.com",
 *             subject = "Welcome!",
 *             body = any()
 *         )
 *     }
 * }
 * ```
 *
 * @param E The type of domain event this handler processes
 */
interface DomainEventHandler<in E : DomainEvent> {
    /**
     * Handles a domain event.
     *
     * This method processes the event to perform side effects such as updating
     * read models, sending notifications, or integrating with external systems.
     *
     * This is a `suspend` function to support non-blocking I/O operations and
     * integration with Kotlin coroutines.
     *
     * ## Idempotency Requirement:
     * This method MUST be idempotent. It should produce the same result when
     * called multiple times with the same event, as events may be delivered
     * more than once due to retries or at-least-once delivery guarantees.
     *
     * ## Error Handling:
     * - Handle expected errors gracefully (log and continue)
     * - Let unexpected errors propagate for framework handling
     * - Consider using a dead letter queue for failed events
     *
     * ## Transaction Boundary:
     * Event handlers typically run outside the main transaction. If you need
     * transactional behavior, manage it within the handler.
     *
     * @param event The domain event to handle
     */
    suspend fun handle(event: E)
}
