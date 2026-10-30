package io.github.avohra25.kcrdt

/** Result of comparing two vector clocks under the happened-before partial order. */
enum class Causality { BEFORE, AFTER, EQUAL, CONCURRENT }

/**
 * An immutable vector clock. Missing entries are treated as zero, and zero
 * entries are never stored, so structurally equal clocks are equal objects.
 */
class VectorClock private constructor(private val entries: Map<ReplicaId, Long>) : StateCrdt<VectorClock> {

    operator fun get(replica: ReplicaId): Long = entries[replica] ?: 0L

    val replicas: Set<ReplicaId> get() = entries.keys

    fun increment(replica: ReplicaId): VectorClock = VectorClock(entries + (replica to get(replica) + 1))

    override fun merge(other: VectorClock): VectorClock {
        val out = HashMap(entries)
        for ((r, c) in other.entries) if (c > (out[r] ?: 0L)) out[r] = c
        return VectorClock(out)
    }

    fun compare(other: VectorClock): Causality {
        var less = false
        var greater = false
        for (r in entries.keys + other.entries.keys) {
            val a = get(r)
            val b = other[r]
            if (a < b) less = true
            if (a > b) greater = true
        }
        return when {
            less && greater -> Causality.CONCURRENT
            less -> Causality.BEFORE
            greater -> Causality.AFTER
            else -> Causality.EQUAL
        }
    }

    /** True if this clock is greater than or equal to [other] in every entry. */
    fun dominates(other: VectorClock): Boolean = compare(other).let { it == Causality.AFTER || it == Causality.EQUAL }

    fun toMap(): Map<ReplicaId, Long> = entries

    override fun equals(other: Any?): Boolean = other is VectorClock && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String =
        entries.toSortedMap().entries.joinToString(", ", "{", "}") { "${it.key}:${it.value}" }

    companion object {
        val EMPTY = VectorClock(emptyMap())

        fun of(vararg pairs: Pair<ReplicaId, Long>): VectorClock = of(mapOf(*pairs))

        fun of(map: Map<ReplicaId, Long>): VectorClock {
            require(map.values.all { it >= 0 }) { "vector clock entries must be non-negative" }
            return VectorClock(map.filterValues { it > 0 })
        }
    }
}
