package io.github.avohra25.kcrdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RegistersTest {
    @Test
    fun `lww register keeps the latest write and breaks ties by replica`() {
        val r = LwwRegister.empty<String>()
        assertNull(r.value)
        val a = r.set("from a", Hlc(10, 0, "a"))
        val b = r.set("from b", Hlc(10, 0, "b"))
        assertEquals("from b", a.merge(b).value)
        assertEquals("from b", b.merge(a).value)
        assertEquals("from b", b.set("stale", Hlc(9, 5, "c")).value)
    }

    @Test
    fun `mv register keeps concurrent writes as siblings`() {
        val base = MvRegister.empty<String>().assign("a", "v1")
        val x = base.assign("a", "left")
        val y = base.assign("b", "right")
        val merged = x.merge(y)
        assertEquals(setOf("left", "right"), merged.values)
        // A write that has seen both siblings resolves the conflict.
        val resolved = merged.assign("c", "both")
        assertEquals(setOf("both"), resolved.values)
        assertEquals(setOf("both"), resolved.merge(x).merge(y).values)
    }

    @Test
    fun `lww register join laws`() = checkJoinLaws { rnd ->
        var r = LwwRegister.empty<Int>()
        repeat(rnd.nextInt(4)) {
            // A real HLC never issues the same timestamp twice, so the value is a function of it.
            val at = Hlc(rnd.nextLong(5), rnd.nextInt(3), REPLICAS.random(rnd))
            r = r.set(at.hashCode() % 10, at)
        }
        r
    }

    @Test
    fun `mv register join laws`() = checkGossipLaws { rnd ->
        gossip(rnd, MvRegister.empty<Int>()) { s, replica, r -> s.assign(replica, r.nextInt(5)) }
    }
}
