package io.github.avohra25.kcrdt

/**
 * Map whose keys are independent last-writer-wins registers. Removal writes a
 * timestamped tombstone so that a stale put cannot resurrect a deleted key.
 */
class LwwMap<K, V : Any> private constructor(
    private val registers: Map<K, LwwRegister<V?>>,
) : StateCrdt<LwwMap<K, V>> {

    operator fun get(key: K): V? = registers[key]?.value

    fun containsKey(key: K): Boolean = get(key) != null

    val keys: Set<K> get() = registers.filterValues { it.value != null }.keys

    fun toMap(): Map<K, V> = registers.mapNotNull { (k, r) -> r.value?.let { k to it } }.toMap()

    fun put(key: K, value: V, at: Hlc): LwwMap<K, V> = write(key, value, at)

    fun remove(key: K, at: Hlc): LwwMap<K, V> = write(key, null, at)

    private fun write(key: K, value: V?, at: Hlc): LwwMap<K, V> {
        val current = registers[key] ?: LwwRegister.empty()
        return LwwMap(registers + (key to current.set(value, at)))
    }

    override fun merge(other: LwwMap<K, V>): LwwMap<K, V> {
        val out = HashMap(registers)
        for ((k, r) in other.registers) out[k] = out[k]?.merge(r) ?: r
        return LwwMap(out)
    }

    override fun equals(other: Any?): Boolean = other is LwwMap<*, *> && other.registers == registers
    override fun hashCode(): Int = registers.hashCode()
    override fun toString(): String = "LwwMap(${toMap()})"

    companion object {
        fun <K, V : Any> empty(): LwwMap<K, V> = LwwMap(emptyMap())
    }
}
