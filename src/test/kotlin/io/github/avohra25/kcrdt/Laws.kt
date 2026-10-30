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

/**
 * Runs a random gossip history over three replicas named a, b and c: each
 * step either applies a local update or merges one replica's state into
 * another. Returns the three (causally consistent) replica states.
 */
fun <T : StateCrdt<T>> gossip(rnd: Random, initial: T, steps: Int = 12, update: (T, ReplicaId, Random) -> T): List<T> {
    val names = listOf("a", "b", "c")
    val states = MutableList(3) { initial }
    repeat(steps) {
        val i = rnd.nextInt(3)
        states[i] = if (rnd.nextInt(4) == 0) states[i].merge(states[rnd.nextInt(3)]) else update(states[i], names[i], rnd)
    }
    return states
}

/** Join-semilattice laws over replica states produced by [gossip]. */
fun <T : StateCrdt<T>> checkGossipLaws(seeds: IntRange = 1..200, gen: (Random) -> List<T>) {
    for (seed in seeds) {
        val (a, b, c) = gen(Random(seed))
        assertEquals(a.merge(b), b.merge(a), "commutativity, seed $seed")
        assertEquals(a.merge(b).merge(c), a.merge(b.merge(c)), "associativity, seed $seed")
        assertEquals(a, a.merge(a), "idempotence, seed $seed")
    }
}
