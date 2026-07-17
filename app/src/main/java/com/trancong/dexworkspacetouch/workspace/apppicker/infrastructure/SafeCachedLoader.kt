package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

internal class SafeCachedLoader<K, V>(
    maximumEntries: Int,
    private val fallback: V,
    private val valueLoader: (K) -> V,
) {
    private val cache = BoundedLruCache<K, V>(maximumEntries)

    fun load(key: K): V = synchronized(cache) {
        cache[key] ?: runCatching { valueLoader(key) }
            .getOrElse { fallback }
            .also { cache[key] = it }
    }
}

private class BoundedLruCache<K, V>(
    private val maximumEntries: Int,
) {
    private val values = LinkedHashMap<K, V>(maximumEntries, LOAD_FACTOR, true)

    init {
        require(maximumEntries > 0) { "maximumEntries must be positive" }
    }

    operator fun get(key: K): V? = values[key]

    operator fun set(key: K, value: V) {
        values[key] = value
        while (values.size > maximumEntries) {
            values.remove(values.entries.first().key)
        }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
