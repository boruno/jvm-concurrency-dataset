import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
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

        while (true) {
            val value = core.getInternal(key)

            if (value != CANNOT_GET) {
                return toValue(value)
            }
        }
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

            if (oldValue == CANNOT_PUT) {
                continue
            }

            if (oldValue != NEEDS_REHASH) return oldValue

            if (value == DEL_VALUE) return NULL_VALUE
            core = core.rehash()
        }
    }

    private class Core internal constructor(val capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        // val map: IntArray = IntArray(2 * capacity)
        val shift: Int

        val map = atomicArrayOfNulls<Any>(2 * capacity)
        private val next: AtomicRef<Core?> = atomic(null)


        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)

            for (i in 0 until 2 * capacity) {
                if (i % 2 == 0) map[i].getAndSet(NULL_KEY)
                else map[i].getAndSet(NULL_VALUE)
            }
        }

        fun getInternal(key: Int): Int {
            var mapVar = tryGetInternal(key)

            if (mapVar == CANNOT_GET) {
                mapVar = next.value!!.tryGetInternal(key)

                if (mapVar == CANNOT_GET) {
                    return CANNOT_GET
                }
            }

            if (mapVar == DEL_VALUE) {
                return 0
            }

            return mapVar
        }

        fun putInternal(key: Int, value: Int): Int {
            var oldVal = tryPutInternal(key, value)

            if (oldVal == LOOK_AT_NEXT) {
                oldVal = next.value!!.tryPutInternal(key, value)

                if (oldVal == LOOK_AT_NEXT) {
                    return CANNOT_PUT
                }
            }

            return oldVal
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))
            val newCore = next.value // map.length is twice the current capacity

            var index = 0
            while (index < map.size) {
                val mapKey = map[index].value as Int
                val mapValue = map[index + 1].value

                if (mapValue is Moved) {
                    val result = newCore!!.putInternal(mapKey, mapValue.value)
                    map[index + 1].compareAndSet(mapValue, LOOK_AT_NEXT)
                    index += 2
                    continue
                }

                if (mapValue !is Int) {
                    throw Exception()
                }

                if (mapValue == LOOK_AT_NEXT) {
                    index += 2
                    continue
                }

                if (mapValue == NULL_VALUE || mapValue == DEL_VALUE) {
                    if (map[index + 1].compareAndSet(mapValue, LOOK_AT_NEXT)) {
                        index += 2
                    }

                    continue
                }

                val movedValue = Moved(mapValue)
                if (!map[index + 1].compareAndSet(mapValue, movedValue)) {
                    continue
                }

                if (isValue(mapValue)) {
                    val result = newCore!!.putInternal(mapKey, mapValue)
                    assert(result != NEEDS_REHASH) { "Unexpected result during rehash: $result" }
                }

                map[index + 1].compareAndSet(movedValue, LOOK_AT_NEXT)
                index += 2
            }
            return newCore!!
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = key % (capacity) * 2 // (key * MAGIC ushr shift) * 2

        private class Moved(val value: Int)

        private fun tryGetInternal(key: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) {
                val mapKey = map[index].value
                if (mapKey == key) break // optimize for successful lookup
                if (mapKey == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value

            val mapVal = map[index + 1].value
            if (mapVal is Moved) {
                return mapVal.value
            }
            if (mapVal !is Int) {
                throw Exception()
            }

            return mapVal
        }

        private fun tryPutInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[index].value
                if (mapKey == key) break
                if (mapKey == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        break
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }

            while (true) {
                // found key -- update value
                val oldValue = map[index + 1].value
                if (oldValue is Moved) {
                    return NEEDS_REHASH
                }

                if (oldValue !is Int) {
                    throw Exception()
                }

                if (oldValue == LOOK_AT_NEXT) {
                    return LOOK_AT_NEXT
                }

                if (map[index + 1].compareAndSet(oldValue, value)) {
                    if (oldValue == DEL_VALUE) return NULL_VALUE

                    return oldValue
                }
            }
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
private const val LOOK_AT_NEXT = -2 // returned by `putInternal` to indicate that this key should be look at next
private const val CANNOT_PUT = -3 // returned by `putInternal` to indicate that operation should be tried again
private const val CANNOT_GET = -3 // returned by `getInternal` to indicate that operation should be tried again

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0