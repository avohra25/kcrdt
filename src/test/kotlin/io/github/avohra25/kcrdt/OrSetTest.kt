package io.github.avohra25.kcrdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrSetTest {
    @Test
    fun `concurrent add wins over remove`() {
        val base = OrSet.empty<String>().add("a", "milk")
        val removed = base.remove("milk")
        val readded = base.add("b", "milk")
        assertTrue("milk" in removed.merge(readded))
        assertTrue("milk" in readded.merge(removed))
    }

    @Test
    fun `observed remove deletes everywhere`() {
        val a = OrSet.empty<String>().add("a", "eggs").add("a", "milk")
        val b = OrSet.empty<String>().merge(a).remove("eggs")
        val m = a.merge(b)
        assertFalse("eggs" in m)
        assertEquals(setOf("milk"), m.elements)
        // Re-delivering a's stale state must not resurrect the removed element.
        assertFalse("eggs" in m.merge(a))
    }

    @Test
    fun `causal context stays compact`() {
        var s = OrSet.empty<Int>()
        repeat(100) { s = s.add("a", it % 7) }
        repeat(7) { s = s.remove(it) }
        assertEquals(0, s.size)
        assertEquals(0, s.context.cloudSize)
    }

    @Test
    fun `matches a sequential set when there is no concurrency`() {
        val rnd = kotlin.random.Random(7)
        var s = OrSet.empty<Int>()
        val model = HashSet<Int>()
        repeat(2_000) {
            val x = rnd.nextInt(20)
            if (rnd.nextBoolean()) { s = s.add("a", x); model += x } else { s = s.remove(x); model -= x }
            assertEquals(model, s.elements)
        }
    }

    @Test
    fun `or-set join laws`() = checkGossipLaws { rnd ->
        gossip(rnd, OrSet.empty<Int>(), steps = 20) { s, replica, r ->
            if (r.nextInt(3) == 0) s.remove(r.nextInt(5)) else s.add(replica, r.nextInt(5))
        }
    }
}
