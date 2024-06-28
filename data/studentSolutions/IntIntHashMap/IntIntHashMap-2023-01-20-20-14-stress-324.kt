import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        val res = core.value.getInternal(key)
        return toValue(res)
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        var index = 0
        while (true) {
            index++
            if (index > 10) throw Exception("overload")
            val oldCore = core.value
            val oldValue = oldCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            if (oldCore.help()) {
                core.compareAndSet(oldCore, oldCore.next.value!!)
            } else {
                core.compareAndSet(oldCore, oldCore.rehash())
            }
        }
    }

    private class Core internal constructor(capacity: Int) {
        val map = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                if (map[index].value == NULL_KEY) return NULL_VALUE
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            val value = map[index + 1].value
            if (value == MOVED) {
                return next.value!!.getInternal(key)
            }
            return toUnLockedValue(value)
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                if (map[index].value == key) {
                    break
                }
                if (map[index].value == NULL_KEY) {
                    if (value == DEL_VALUE) return NULL_VALUE
                    if (map[index].compareAndSet(NULL_VALUE, key)) break
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            val oldValue = map[index + 1].value

            if (next.value != null) {
                return NEEDS_REHASH
            }

            if (oldValue == MOVED) {
                return NEEDS_REHASH
            }

            if (isLockedValue(oldValue)) {
                return NEEDS_REHASH
            }

            if (!map[index + 1].compareAndSet(oldValue, value)) return NEEDS_REHASH
            else return oldValue

        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))
            helpInternal()
            return next.value!!
        }

        fun help(): Boolean {
            return if (next.value == null) false
            else {
                helpInternal()
                true
            }
        }

        private fun helpInternal() {
            var index = 0
            while (index < map.size) {
                val key = map[index].value
                val value = map[index + 1].value
                if (value == NULL_VALUE) {
                    if (!map[index + 1].compareAndSet(toLockedValue(value), MOVED)) continue
                } else {
                    if (!map[index + 1].compareAndSet(value, toLockedValue(value))) {
                        if (!isToMove(map[index + 1].value)) continue
                    }
                }
                next.value!!.placeInternal(key, toUnLockedValue(value))
                map[index + 1].compareAndSet(toLockedValue(value), MOVED)
                index += 2
            }
        }

        fun placeInternal(key: Int, value: Int): Boolean {
            if (value == MOVED) return false
            if (value == DEL_VALUE) return false
            val transformedValue = toUnLockedValue(value)

            var index = index(key)
            var probes = 0
            while (true) {
                if (map[index].value == NULL_KEY) {
                    if (map[index].compareAndSet(NULL_VALUE, key)) break
                }
                if (map[index].value == key) { // found key
                    break
                }
                if (++probes >= MAX_PROBES) return false
                if (index == 0) index = map.size
                index -= 2
            }
            return map[index + 1].compareAndSet(NULL_VALUE, transformedValue)
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val MOVED = Int.MIN_VALUE // value moved


private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)
private fun toValue(value: Int): Int = if (isValue(value)) value else 0
private fun isLockedValue(value: Int): Boolean = value in ((MOVED + 1)..-1)
private fun toLockedValue(value: Int): Int = if (value < 0) value else -value
private fun toUnLockedValue(value: Int): Int {
    if (value in 1 until DEL_VALUE) {
        return value
    }
    if (value in (MOVED + 1)..-1) {
        return -value
    }
    return 0
}
private fun isToMove(value: Int): Boolean {
    if (value in 1 until DEL_VALUE) {
        return true
    }
    if (value in (MOVED + 1)..-1) {
        return true
    }
    return false
}