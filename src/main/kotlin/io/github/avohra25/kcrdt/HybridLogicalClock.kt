package io.github.avohra25.kcrdt

/**
 * A hybrid logical clock timestamp (Kulkarni et al., 2014): physical time in
 * milliseconds plus a logical counter, tie-broken by replica id so that every
 * timestamp issued in the system is unique and totally ordered.
 */
data class Hlc(val millis: Long, val logical: Int, val replica: ReplicaId) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::millis, Hlc::logical, Hlc::replica)

    override fun toString(): String = "$millis.$logical@$replica"
}

class ClockDriftException(message: String) : IllegalStateException(message)

/**
 * A stateful hybrid logical clock for one replica.
 *
 * @param wallClock source of physical time (injectable for deterministic tests)
 * @param maxDriftMillis remote timestamps further than this ahead of local wall
 *        time are rejected instead of dragging the clock forward
 */
class HybridLogicalClock(
    val replica: ReplicaId,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val maxDriftMillis: Long = 60_000,
) {
    var last: Hlc = Hlc(0, 0, replica)
        private set

    /** Issues a timestamp for a local event or an outgoing message. */
    fun now(): Hlc {
        val wall = wallClock()
        last = if (wall > last.millis) Hlc(wall, 0, replica) else Hlc(last.millis, bump(last.logical), replica)
        return last
    }

    /** Folds in a timestamp received from another replica and returns the new local time. */
    fun receive(remote: Hlc): Hlc {
        val wall = wallClock()
        if (remote.millis - wall > maxDriftMillis) {
            throw ClockDriftException("remote clock ${remote.millis} is ${remote.millis - wall} ms ahead of local wall time")
        }
        val millis = maxOf(wall, last.millis, remote.millis)
        val logical = when {
            millis == last.millis && millis == remote.millis -> bump(maxOf(last.logical, remote.logical))
            millis == last.millis -> bump(last.logical)
            millis == remote.millis -> bump(remote.logical)
            else -> 0
        }
        last = Hlc(millis, logical, replica)
        return last
    }

    private fun bump(logical: Int): Int {
        if (logical == Int.MAX_VALUE) throw ClockDriftException("logical counter overflow")
        return logical + 1
    }
}
