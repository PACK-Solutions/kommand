package com.ps.cqrs.domain

/**
 * Base abstract class for all entities in the domain model.
 *
 * An **Entity** is a domain object that has a unique identity that persists over time,
 * even if its attributes change. Two entities are considered equal if they have the same ID,
 * regardless of their other properties.
 *
 * ## Key Characteristics:
 * - **Identity-based equality**: Entities are compared by their ID, not by their attributes.
 * - **Mutable state**: Unlike Value Objects, entities can change their state over time.
 * - **Lifecycle**: Entities have a lifecycle (created, modified, deleted) tracked through their identity.
 *
 * @param ID The type of the entity's unique identifier. This is typically a value object
 *           (e.g., `UserId`, `OrderId`) or a simple type (e.g., `String`, `UUID`).
 */
public abstract class Entity<ID> {
    /**
     * The unique identifier of this entity.
     *
     * This ID must be immutable and should uniquely identify the entity throughout its lifecycle.
     * It is used for equality comparison and hash code calculation.
     */
    public abstract val id: ID

    /**
     * Compares this entity with another object for equality based on identity.
     *
     * Two entities are considered equal if and only if:
     * 1. They are of the same type (same class)
     * 2. They have the same ID
     *
     * This ensures that entity equality is based on identity, not on attribute values.
     *
     * @param other The object to compare with this entity
     * @return `true` if the objects are equal based on identity, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Entity<*>

        return id == other.id
    }

    /**
     * Returns a hash code value for this entity based on its ID.
     *
     * The hash code is calculated from the entity's ID to ensure consistency with
     * the `equals()` method. Entities with the same ID will have the same hash code.
     *
     * @return A hash code value for this entity
     */
    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    /**
     * Returns a string representation of this entity.
     *
     * The default implementation returns the class name and ID. Subclasses can override
     * this method to provide more detailed information.
     *
     * @return A string representation of this entity
     */
    override fun toString(): String {
        return "${this::class.simpleName}(id=$id)"
    }
}
