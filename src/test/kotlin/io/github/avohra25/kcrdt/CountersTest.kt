package io.github.avohra25.kcrdt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CountersTest {
    @Test
    fun `gcounter sums per-replica slots`() {
        val a = GCounter.EMPTY.increment("a", 3)
        val b = GCounter.EMPTY.increment("b", 4).increment("b")
        val m = a.merge(b)
        assertEquals(8, m.value)
        assertEquals(m, m.merge(a)) // re-delivering old state changes nothing
        assertFailsWith<IllegalArgumentException> { a.increment("a", -1) }
    }

    @Test
    fun `pncounter converges after concurrent increments and decrements`() {
        var a = PNCounter.EMPTY
        var b = PNCounter.EMPTY
        a = a.increment("a", 10).decrement("a", 3)
        b = b.decrement("b", 5)
        assertEquals(a.merge(b), b.merge(a))
        assertEquals(2, a.merge(b).value)
    }

    @Test
    fun `gcounter join laws`() = checkJoinLaws { rnd ->
        var c = GCounter.EMPTY
        repeat(rnd.nextInt(6)) { c = c.increment(REPLICAS.random(rnd), rnd.nextLong(0, 5)) }
        c
    }

    @Test
    fun `pncounter join laws`() = checkJoinLaws { rnd ->
        var c = PNCounter.EMPTY
        repeat(rnd.nextInt(6)) { c = c.increment(REPLICAS.random(rnd), rnd.nextLong(-5, 6)) }
        c
    }
}
