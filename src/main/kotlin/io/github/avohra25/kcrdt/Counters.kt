package io.github.avohra25.kcrdt

/** Grow-only counter: one monotonically increasing slot per replica. */
class GCounter private constructor(private val slots: Map<ReplicaId, Long>) : StateCrdt<GCounter> {

    val value: Long get() = slots.values.sum()

    fun increment(replica: ReplicaId, amount: Long = 1): GCounter {
        require(amount >= 0) { "GCounter can only grow (amount=$amount)" }
        if (amount == 0L) return this
        return GCounter(slots + (replica to Math.addExact(slots[replica] ?: 0L, amount)))
    }

    fun contribution(replica: ReplicaId): Long = slots[replica] ?: 0L

    override fun merge(other: GCounter): GCounter {
        val out = HashMap(slots)
        for ((r, c) in other.slots) if (c > (out[r] ?: 0L)) out[r] = c
        return GCounter(out)
    }

    override fun equals(other: Any?): Boolean = other is GCounter && other.slots == slots
    override fun hashCode(): Int = slots.hashCode()
    override fun toString(): String = "GCounter($value)"

    companion object {
        val EMPTY = GCounter(emptyMap())
    }
}

/** Counter supporting increments and decrements, built from two [GCounter]s. */
class PNCounter private constructor(private val p: GCounter, private val n: GCounter) : StateCrdt<PNCounter> {

    val value: Long get() = p.value - n.value

    fun increment(replica: ReplicaId, amount: Long = 1): PNCounter = when {
        amount >= 0 -> PNCounter(p.increment(replica, amount), n)
        else -> PNCounter(p, n.increment(replica, -amount))
    }

    fun decrement(replica: ReplicaId, amount: Long = 1): PNCounter = increment(replica, -amount)

    override fun merge(other: PNCounter): PNCounter = PNCounter(p.merge(other.p), n.merge(other.n))

    override fun equals(other: Any?): Boolean = other is PNCounter && other.p == p && other.n == n
    override fun hashCode(): Int = 31 * p.hashCode() + n.hashCode()
    override fun toString(): String = "PNCounter($value)"

    companion object {
        val EMPTY = PNCounter(GCounter.EMPTY, GCounter.EMPTY)
    }
}
