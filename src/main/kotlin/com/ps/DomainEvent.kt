package com.ps

/**
 * Marker interface for domain events emitted while handling commands.
 *
 * Events are collected on [CommandResult.events] so that middleware can
 * observe and publish them using your application's preferred mechanism.
 */
interface DomainEvent
