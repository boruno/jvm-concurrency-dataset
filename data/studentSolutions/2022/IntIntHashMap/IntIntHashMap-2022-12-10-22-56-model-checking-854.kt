import kotlinx.atomicfu.*

/**
 * Int-to-Int hash map with open addressing and linear probes.
 */
class IntIntHashMap {
    private var core = Core(INITIAL_CAPACITY)

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.getInternal(key))
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
        while (true) {
            val oldValue = core.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core = core.rehash()
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        //val map: IntArray = IntArray(2 * capacity)
        val shift: Int

        val map = atomicArrayOfNulls<Int>(2 * capacity)
        private val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)

            for (i in 0 until 2 * capacity) {
                if (i % 2 == 0) {
                    map[i].getAndSet(NULL_KEY)
                }
                else {
                    map[i].getAndSet(NULL_VALUE)
                }
            }
        }

        // Next problem like array
        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) { // optimize for successful lookup
                val mapKey = map[index].value

                if (mapKey == key) {
                    break
                }
                if (mapKey == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value
            return map[index + 1].value ?: NULL_VALUE
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val mapKey = map[index].value

                if (mapKey == key) {
                    break
                }

                if (mapKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (map[index].compareAndSet(NULL_VALUE, key) ||
                        map[index].compareAndSet(DEL_VALUE, key)) {
                        break
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- update value

            while (true) {
                val oldValue = map[index + 1].value
                if (map[index + 1].compareAndSet(oldValue, value))

                return oldValue ?: NULL_VALUE
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))

            val newCore = next.value
            var index = 0
            while (index < map.size) {
                val mapValue = map[index + 1].value

                if (isValue(mapValue ?: NULL_VALUE)) {
                    val result = newCore!!.putInternal(map[index].value ?: NULL_KEY, mapValue ?: NULL_VALUE)
                    assert(result == 0 || result == mapValue) { "Unexpected result during rehash: $result" }
                }
                index += 2
            }
            return newCore!!
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
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0