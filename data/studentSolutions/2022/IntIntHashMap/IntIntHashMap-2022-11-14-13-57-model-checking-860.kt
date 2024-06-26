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
        return toValue(core.value.getInternal(key))
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
        return toValue(core.value.putInternal(key, value))
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
        return toValue(core.value.putInternal(key, DEL_VALUE))
    }

//    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
//        while (true) {
//            val oldValue = core.value.putInternal(key, value)
//            if (oldValue != NEEDS_REHASH) return oldValue
//            core.value.rehash()
//
//        }
//    }

    private fun updateCore() {
        while (true) {
            val curCore = core.value
            if (curCore.rehashed) {
                core.compareAndSet(curCore, curCore.next.value!!)
            } else {
                break
            }
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int

        val next: AtomicRef<Core?> = atomic(null)

//        var needsRehashing = false
        var rehashed = false

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                if (map[index].value == NULL_KEY || ++probes >= MAX_PROBES) {
                    if (next.value == null) {
                        return NULL_VALUE
                    }
                    return next.value!!.getInternal(key)
                }
                if (index == 0) index = map.size
                index -= 2
            }
            val curValue = map[index + 1].value
            if (isFixed(curValue)) {
                val nextValue = next.value!!.getInternal(key)
                if (nextValue == NULL_VALUE)
                    return getFixed(curValue)
                else
                    return nextValue
            }
            return curValue
        }

//        fun putInternal(key: Int, value: Int): Int {
//            var index = index(key)
//            var probes = 0
//            while (map[index] != key) { // optimize for successful lookup
//                if (map[index] == NULL_KEY) {
//                    // not found -- claim this slot
//                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
//                    map[index] = key
//                    break
//                }
//                if (++probes >= MAX_PROBES) return NEEDS_REHASH
//                if (index == 0) index = map.size
//                index -= 2
//            }
//            // found key -- update value
//            val oldValue = map[index + 1]
//            map[index + 1] = value
//            return oldValue
//        }



        fun putKey(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                if (map[index].value == NULL_KEY)
                    if (map[index].compareAndSet(NULL_KEY, key))
                        break

                if (++probes >= MAX_PROBES) {
                    if (next.value != null) {
                        return next.value!!.putKey(key)
                    }
                    return NEEDS_REHASH
                }
                if (index == 0) index = map.size
                index -= 2
            }
            return 0
        }

        fun putValueAfterRehash(key: Int, value: Int) {
            val index = findIndex(key)
            map[index + 1].compareAndSet(NULL_VALUE, value)
        }

        fun putValueForce(key: Int, value: Int): Int {
            val index = findIndex(key)
            while (true) {
                val oldVal = map[index + 1].value
                if (isFixed(oldVal)) {
                    return next.value!!.putValueForce(key, value)
                }
                if (map[index + 1].compareAndSet(oldVal, value)) {
                    return oldVal
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            while (true) {
                val result = putKey(key)
                if (result == NEEDS_REHASH) {
                    rehash()
                } else {
                    return putValueForce(key, value)
                }
            }
        }

        fun findIndex(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                if (++probes >= MAX_PROBES) {
                    return -2
                }
                if (index == 0) index = map.size
                index -= 2
            }
            return index
        }

        fun rehash() {
            val newCore = Core(map.size)
            if (next.compareAndSet(null, newCore)) {
                movePairs()
                rehashed = true
            }
        }

        fun movePairs() {
            var index = 0
            val newCore = next.value!!
            while (index < map.size) {
                while (true) {
                    val oldVal = map[index + 1].value
                    if (isValue(oldVal)) {
                        if (map[index + 1].compareAndSet(oldVal, getFixed(oldVal))) {
                            moveKey(index)
                            newCore.putValueAfterRehash(map[index].value, oldVal)
                            break
                        }
                    }
                    if (map[index + 1].compareAndSet(NULL_VALUE, MOVED_EMPTY)) {
                        break
                    }
                }
                index += 2
            }
        }

        fun moveKey(index: Int) {
            val newCore = next.value!!
            while (true) {
                val result = newCore.putKey(map[index].value)
                if (result == NEEDS_REHASH) {
                    newCore.rehash()
                } else {
                    break
                }
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val MOVED_EMPTY = Int.MIN_VALUE // rehashing moved empty cell
private const val NEEDS_REHASH = -Int.MAX_VALUE

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

private fun isFixed(value: Int): Boolean = value != Int.MIN_VALUE && isValue(-value)

private fun getFixed(value: Int): Int = -value