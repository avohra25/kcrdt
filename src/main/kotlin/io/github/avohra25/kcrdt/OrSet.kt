package io.github.avohra25.kcrdt

/**
 * Add-wins observed-remove set (OR-Set), in its optimized dot-based form.
 *
 * Every add tags the element with a fresh [Dot]; a remove deletes only the
 * dots it has observed. A concurrent add therefore survives a remove ("add
 * wins"), and removes leave no per-element tombstones: the causal context
 * alone remembers which dots were seen and deleted.
 */
class OrSet<E> private constructor(
    private val entries: Map<E, Set<Dot>>,
    val context: DotContext,
) : StateCrdt<OrSet<E>> {

    val elements: Set<E> get() = entries.keys

    operator fun contains(element: E): Boolean = element in entries

    val size: Int get() = entries.size

    fun add(replica: ReplicaId, element: E): OrSet<E> {
        val dot = context.nextDot(replica)
        // The new dot replaces every dot of this element we have observed.
        return OrSet(entries + (element to setOf(dot)), context.add(dot))
    }

    fun remove(element: E): OrSet<E> = OrSet(entries - element, context)

    fun clear(): OrSet<E> = OrSet(emptyMap(), context)

    override fun merge(other: OrSet<E>): OrSet<E> {
        val out = HashMap<E, Set<Dot>>()
        for (e in entries.keys + other.entries.keys) {
            val mine = entries[e].orEmpty()
            val theirs = other.entries[e].orEmpty()
            val dots = (mine intersect theirs) +
                mine.filterTo(HashSet()) { it !in other.context } +
                theirs.filterTo(HashSet()) { it !in context }
            if (dots.isNotEmpty()) out[e] = dots
        }
        return OrSet(out, context.merge(other.context))
    }

    override fun equals(other: Any?): Boolean = other is OrSet<*> && other.entries == entries && other.context == context
    override fun hashCode(): Int = 31 * entries.hashCode() + context.hashCode()
    override fun toString(): String = "OrSet($elements)"

    companion object {
        fun <E> empty(): OrSet<E> = OrSet(emptyMap(), DotContext.EMPTY)
    }
}
