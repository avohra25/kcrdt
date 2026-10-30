package io.github.avohra25.kcrdt

/** Identifier of a replica (a process, device or user session). */
typealias ReplicaId = String

/**
 * A state-based CRDT (convergent replicated data type).
 *
 * [merge] must be a join on a semilattice: commutative, associative and
 * idempotent. Instances are immutable; every operation returns a new value.
 */
interface StateCrdt<T : StateCrdt<T>> {
    fun merge(other: T): T
}
