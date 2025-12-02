package com.ps.cqrs.domain

/**
 * Marker interface for Value Objects in the domain model.
 *
 * A **Value Object** is a domain concept defined entirely by its attributes,
 * with no conceptual identity. Two value objects are considered equal if all their
 * attributes are equal.
 *
 * ## Key Characteristics:
 * - **Attribute-based equality**: Value objects are compared by their attributes, not by identity.
 * - **Immutable**: Value objects should be immutable. Once created, their state cannot change.
 * - **Replaceable**: If you need to "change" a value object, you create a new instance.
 *
 * ## Example
 * ```kotlin
 * // A classic Value Object
 * @JvmInline
 * value class Email private constructor(val value: String) : ValueObject {
 *     companion object {
 *         fun parse(raw: String): Email {
 *             require("@" in raw) { "Invalid email" }
 *             return Email(raw.lowercase())
 *         }
 *     }
 * }
 *
 * val a = Email.parse("User@Example.com")
 * val b = Email.parse("user@example.com")
 * check(a == b) // true — compared by value
 * ```
 */
interface ValueObject
