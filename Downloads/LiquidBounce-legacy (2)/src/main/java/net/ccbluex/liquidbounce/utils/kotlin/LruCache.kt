package net.ccbluex.liquidbounce.utils.kotlin

/**
 * @see org.junit.platform.commons.util.LruCache
 */
class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(maxSize + 1, 1f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
        return size > maxSize
    }
}
