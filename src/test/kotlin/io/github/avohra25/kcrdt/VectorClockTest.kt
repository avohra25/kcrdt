package io.github.avohra25.kcrdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VectorClockTest {
    @Test
    fun `increment and compare`() {
        val a = VectorClock.EMPTY.increment("a")
        val ab = a.increment("b")
        assertEquals(Causality.BEFORE, a.compare(ab))
        assertEquals(Causality.AFTER, ab.compare(a))
        assertEquals(Causality.EQUAL, ab.compare(VectorClock.of("a" to 1, "b" to 1)))
        val ac = a.increment("c")
        assertEquals(Causality.CONCURRENT, ab.compare(ac))
        assertEquals(VectorClock.of("a" to 1, "b" to 1, "c" to 1), ab.merge(ac))
        assertTrue(ab.merge(ac).dominates(ab))
    }

    @Test
    fun `zero entries are normalised away`() {
        assertEquals(VectorClock.EMPTY, VectorClock.of("a" to 0))
        assertFailsWith<IllegalArgumentException> { VectorClock.of("a" to -1) }
    }

    @Test
    fun `merge is a join`() = checkJoinLaws { rnd ->
        var vc = VectorClock.EMPTY
        repeat(rnd.nextInt(8)) { vc = vc.increment(REPLICAS.random(rnd)) }
        vc
    }
}
