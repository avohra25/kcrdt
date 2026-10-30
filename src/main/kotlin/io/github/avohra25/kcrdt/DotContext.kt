package io.github.avohra25.kcrdt

/** A unique event identifier: the [counter]-th event issued by [replica]. */
data class Dot(val replica: ReplicaId, val counter: Long) {
    init {
        require(counter > 0) { "dot counters start at 1" }
    }

    override fun toString(): String = "$replica:$counter"
}

/**
 * The set of events a replica has observed, stored compactly as a version
 * vector (every dot up to N is seen) plus a "dot cloud" of out-of-order dots.
 * This is the causal context used by delta/dot-based CRDTs (Almeida et al.).
 */
class DotContext private constructor(
    private val vector: Map<ReplicaId, Long>,
    private val cloud: Set<Dot>,
) : StateCrdt<DotContext> {

    operator fun contains(dot: Dot): Boolean = dot.counter <= (vector[dot.replica] ?: 0L) || dot in cloud

    /** The next fresh dot for [replica]; call [add] to record it. */
    fun nextDot(replica: ReplicaId): Dot = Dot(replica, (vector[replica] ?: 0L) + 1)

    fun add(dot: Dot): DotContext = DotContext(vector, cloud + dot).compact()

    override fun merge(other: DotContext): DotContext {
        val v = HashMap(vector)
        for ((r, c) in other.vector) if (c > (v[r] ?: 0L)) v[r] = c
        return DotContext(v, cloud + other.cloud).compact()
    }

    /** Folds contiguous cloud dots into the vector and drops dots it already covers. */
    private fun compact(): DotContext {
        if (cloud.isEmpty()) return this
        val v = HashMap(vector)
        val remaining = cloud.sortedWith(compareBy(Dot::replica, Dot::counter)).toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val it = remaining.iterator()
            while (it.hasNext()) {
                val d = it.next()
                val seen = v[d.replica] ?: 0L
                when {
                    d.counter <= seen -> it.remove()
                    d.counter == seen + 1 -> { v[d.replica] = d.counter; it.remove(); changed = true }
                }
            }
        }
        return DotContext(v, remaining.toSet())
    }

    val cloudSize: Int get() = cloud.size

    override fun equals(other: Any?): Boolean = other is DotContext && other.vector == vector && other.cloud == cloud
    override fun hashCode(): Int = 31 * vector.hashCode() + cloud.hashCode()
    override fun toString(): String = "DotContext(vv=$vector, cloud=$cloud)"

    companion object {
        val EMPTY = DotContext(emptyMap(), emptySet())
    }
}
