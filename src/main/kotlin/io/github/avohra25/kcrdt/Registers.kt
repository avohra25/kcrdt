package io.github.avohra25.kcrdt

/**
 * Last-writer-wins register. Concurrent writes are resolved by the larger
 * [Hlc] timestamp; because HLC timestamps carry the replica id, ties are
 * impossible and every replica picks the same winner.
 */
class LwwRegister<T> private constructor(val value: T?, val timestamp: Hlc?) : StateCrdt<LwwRegister<T>> {

    fun set(value: T, at: Hlc): LwwRegister<T> = merge(LwwRegister(value, at))

    override fun merge(other: LwwRegister<T>): LwwRegister<T> = when {
        other.timestamp == null -> this
        timestamp == null || other.timestamp > timestamp -> other
        else -> this
    }

    override fun equals(other: Any?): Boolean =
        other is LwwRegister<*> && other.timestamp == timestamp && other.value == value

    override fun hashCode(): Int = 31 * (timestamp?.hashCode() ?: 0) + (value?.hashCode() ?: 0)
    override fun toString(): String = "LwwRegister($value @ $timestamp)"

    companion object {
        fun <T> empty(): LwwRegister<T> = LwwRegister(null, null)
    }
}

/**
 * Multi-value register. Concurrent writes are all kept (as siblings) until a
 * later write that has observed them replaces them, so no update is silently
 * lost; the application decides how to resolve [values] when there are several.
 */
class MvRegister<T> private constructor(private val entries: Set<Pair<T, VectorClock>>) : StateCrdt<MvRegister<T>> {

    val values: Set<T> get() = entries.mapTo(LinkedHashSet()) { it.first }

    /** The causal context a new write from this state will supersede. */
    val clock: VectorClock get() = entries.fold(VectorClock.EMPTY) { acc, e -> acc.merge(e.second) }

    fun assign(replica: ReplicaId, value: T): MvRegister<T> =
        MvRegister(setOf(value to clock.increment(replica)))

    override fun merge(other: MvRegister<T>): MvRegister<T> {
        val all = entries + other.entries
        val kept = all.filterTo(HashSet()) { (_, vc) ->
            all.none { (_, o) -> o.compare(vc) == Causality.AFTER }
        }
        return MvRegister(kept)
    }

    override fun equals(other: Any?): Boolean = other is MvRegister<*> && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "MvRegister($values)"

    companion object {
        fun <T> empty(): MvRegister<T> = MvRegister(emptySet())
    }
}
