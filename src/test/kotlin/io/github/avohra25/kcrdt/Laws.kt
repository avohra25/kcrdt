package io.github.avohra25.kcrdt

import kotlin.random.Random
import kotlin.test.assertEquals

/** Checks the semilattice laws for a state CRDT over randomly generated replica states. */
fun <T : StateCrdt<T>> checkJoinLaws(seeds: IntRange = 1..200, gen: (Random) -> T) {
    for (seed in seeds) {
        val rnd = Random(seed)
        val a = gen(rnd)
        val b = gen(rnd)
        val c = gen(rnd)
        assertEquals(a.merge(b), b.merge(a), "commutativity, seed $seed")
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)), "associativity, seed $seed")
        assertEquals(a, a.merge(a), "idempotence, seed $seed")
    }
}

val REPLICAS = listOf("a", "b", "c", "d")
