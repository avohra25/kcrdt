package io.github.avohra25.kcrdt

/**
 * Identifier of an element in an [Rga]: a Lamport timestamp tie-broken by
 * replica. Every element inserted after X gets an id larger than X's.
 */
data class ElementId(val counter: Long, val replica: ReplicaId) : Comparable<ElementId> {
    override fun compareTo(other: ElementId): Int =
        compareValuesBy(this, other, ElementId::counter, ElementId::replica)

    override fun toString(): String = "$counter@$replica"
}

/** An operation broadcast between [Rga] replicas. */
sealed interface RgaOp<out T> {
    /** Insert [value] immediately after [after] (or at the head when null). */
    data class Insert<T>(val id: ElementId, val after: ElementId?, val value: T) : RgaOp<T>

    /** Tombstone the element [target]. */
    data class Delete(val target: ElementId) : RgaOp<Nothing>
}

/**
 * Replicated Growable Array (Roh et al., 2011): an operation-based sequence
 * CRDT suitable for collaborative text editing.
 *
 * Local edits return the [RgaOp] to broadcast; remote ops are fed to [apply].
 * Delivery may be duplicated or reordered: duplicates are ignored, and ops
 * whose causal dependency (the anchor element or the delete target) has not
 * arrived yet are buffered until it does. Deleted elements stay as tombstones
 * so that later inserts anchored on them still resolve.
 */
class Rga<T>(val replica: ReplicaId) {

    private class Node<T>(val id: ElementId, val value: T, var deleted: Boolean = false)

    private val nodes = ArrayList<Node<T>>()
    private val byId = HashMap<ElementId, Node<T>>()
    private val pending = ArrayList<RgaOp<T>>()
    private val history = ArrayList<RgaOp<T>>()
    private var clock = 0L

    /** Number of visible (non-deleted) elements. */
    val size: Int get() = nodes.count { !it.deleted }

    /** Number of tombstones retained. */
    val tombstones: Int get() = nodes.count { it.deleted }

    /** Remote ops waiting for a causal dependency. */
    val pendingCount: Int get() = pending.size

    fun toList(): List<T> = nodes.filter { !it.deleted }.map { it.value }

    operator fun get(index: Int): T = visibleNode(index).value

    /** Inserts [value] so that it ends up at visible position [index]. */
    fun insert(index: Int, value: T): RgaOp.Insert<T> {
        if (index < 0 || index > size) throw IndexOutOfBoundsException("index $index, size $size")
        val after = if (index == 0) null else visibleNode(index - 1).id
        val op = RgaOp.Insert(ElementId(clock + 1, replica), after, value)
        check(apply(op))
        return op
    }

    fun append(value: T): RgaOp.Insert<T> = insert(size, value)

    /** Deletes the element at visible position [index]. */
    fun delete(index: Int): RgaOp.Delete {
        val op = RgaOp.Delete(visibleNode(index).id)
        check(apply(op))
        return op
    }

    /**
     * Applies a local or remote op. Returns true if it took effect now, false if
     * it was a duplicate or had to be buffered.
     */
    fun apply(op: RgaOp<T>): Boolean {
        if (!ready(op)) {
            if (!isDuplicate(op) && op !in pending) pending += op
            return false
        }
        if (isDuplicate(op)) return false
        integrate(op)
        drainPending()
        return true
    }

    /** Every op applied so far, in an order that is causally safe to replay on a fresh replica. */
    fun operations(): List<RgaOp<T>> = history.toList()

    private fun ready(op: RgaOp<T>): Boolean = when (op) {
        is RgaOp.Insert -> op.after == null || op.after in byId
        is RgaOp.Delete -> op.target in byId
    }

    private fun isDuplicate(op: RgaOp<T>): Boolean = when (op) {
        is RgaOp.Insert -> op.id in byId
        is RgaOp.Delete -> byId[op.target]?.deleted == true
    }

    private fun integrate(op: RgaOp<T>) {
        when (op) {
            is RgaOp.Insert -> {
                var pos = if (op.after == null) 0 else indexOfNode(op.after) + 1
                // Skip over concurrent siblings (and their subtrees) with larger ids:
                // newer inserts at the same anchor go first.
                while (pos < nodes.size && nodes[pos].id > op.id) pos++
                val node = Node(op.id, op.value)
                nodes.add(pos, node)
                byId[op.id] = node
                clock = maxOf(clock, op.id.counter)
            }
            is RgaOp.Delete -> byId.getValue(op.target).deleted = true
        }
        history += op
    }

    private fun drainPending() {
        var progressed = true
        while (progressed && pending.isNotEmpty()) {
            progressed = false
            val it = pending.iterator()
            val ready = ArrayList<RgaOp<T>>()
            while (it.hasNext()) {
                val op = it.next()
                if (ready(op)) { it.remove(); ready += op }
            }
            for (op in ready) if (!isDuplicate(op)) { integrate(op); progressed = true }
        }
    }

    private fun indexOfNode(id: ElementId): Int {
        val target = byId.getValue(id)
        return nodes.indexOfFirst { it === target }
    }

    private fun visibleNode(index: Int): Node<T> {
        var seen = 0
        for (n in nodes) {
            if (n.deleted) continue
            if (seen == index) return n
            seen++
        }
        throw IndexOutOfBoundsException("index $index, size $seen")
    }
}

/** The visible text of a character sequence. */
fun Rga<Char>.text(): String = toList().joinToString("")

/** Inserts [text] at [index] as a run of characters; returns the ops to broadcast. */
fun Rga<Char>.insertText(index: Int, text: String): List<RgaOp<Char>> =
    text.mapIndexed { i, ch -> insert(index + i, ch) }

/** Deletes [count] characters starting at [index]; returns the ops to broadcast. */
fun Rga<Char>.deleteRange(index: Int, count: Int): List<RgaOp<Char>> = List(count) { delete(index) }
