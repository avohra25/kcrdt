package io.github.avohra25.kcrdt

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HybridLogicalClockTest {
    @Test
    fun `timestamps are strictly increasing even when wall time stalls or goes backwards`() {
        var wall = 1_000L
        val clock = HybridLogicalClock("a", { wall })
        val t1 = clock.now()
        val t2 = clock.now()
        wall = 900
        val t3 = clock.now()
        wall = 2_000
        val t4 = clock.now()
        assertTrue(t1 < t2 && t2 < t3 && t3 < t4)
        assertEquals(Hlc(1_000, 2, "a"), t3)
        assertEquals(Hlc(2_000, 0, "a"), t4)
    }

    @Test
    fun `receive moves past the remote timestamp`() {
        val clock = HybridLogicalClock("a", { 1_000L })
        val remote = Hlc(1_500, 7, "b")
        val t = clock.receive(remote)
        assertTrue(t > remote)
        assertEquals(Hlc(1_500, 8, "a"), t)
        assertTrue(clock.now() > t)
    }

    @Test
    fun `excessive drift is rejected`() {
        val clock = HybridLogicalClock("a", { 0L }, maxDriftMillis = 100)
        assertFailsWith<ClockDriftException> { clock.receive(Hlc(101, 0, "b")) }
        clock.receive(Hlc(100, 0, "b"))
    }

    @Test
    fun `causality is preserved across random message exchange`() {
        val rnd = Random(42)
        val wall = LongArray(3) { 10_000L }
        val clocks = List(3) { i -> HybridLogicalClock("r$i", { wall[i] }) }
        val outbox = ArrayList<Hlc>()
        val previous = arrayOfNulls<Hlc>(3)
        repeat(5_000) {
            val i = rnd.nextInt(3)
            wall[i] += rnd.nextLong(-5, 6) // skewed, jittery clocks
            val t = if (outbox.isNotEmpty() && rnd.nextBoolean()) {
                val msg = outbox.removeAt(rnd.nextInt(outbox.size))
                clocks[i].receive(msg).also { assertTrue(it > msg, "receive must order after the message") }
            } else {
                clocks[i].now().also { outbox += it }
            }
            previous[i]?.let { assertTrue(t > it, "per-replica monotonicity") }
            previous[i] = t
        }
    }
}
