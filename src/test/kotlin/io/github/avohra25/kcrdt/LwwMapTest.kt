package io.github.avohra25.kcrdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LwwMapTest {
    @Test
    fun `puts and removes resolve by timestamp`() {
        val m0 = LwwMap.empty<String, Int>().put("x", 1, Hlc(1, 0, "a"))
        val removed = m0.remove("x", Hlc(2, 0, "b"))
        val stalePut = m0.put("x", 99, Hlc(1, 1, "c"))
        assertNull(removed.merge(stalePut)["x"])
        assertNull(stalePut.merge(removed)["x"])
        val newer = m0.put("x", 3, Hlc(3, 0, "c"))
        assertEquals(3, removed.merge(newer)["x"])
        assertEquals(mapOf("x" to 3), newer.merge(removed).toMap())
    }

    @Test
    fun `lww map join laws`() = checkJoinLaws { rnd ->
        var m = LwwMap.empty<Int, Int>()
        repeat(rnd.nextInt(6)) {
            val at = Hlc(rnd.nextLong(4), rnd.nextInt(2), REPLICAS.random(rnd))
            // Timestamps are unique per write in real use, so derive key, kind and value from them.
            val key = at.hashCode().mod(3)
            m = if (at.hashCode().mod(4) == 0) m.remove(key, at) else m.put(key, at.hashCode().mod(10), at)
        }
        m
    }
}
