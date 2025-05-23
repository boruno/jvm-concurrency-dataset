import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicIntegerArray

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
        val map: AtomicIntegerArray = AtomicIntegerArray(2 * capacity)
        val next: AtomicRef<Core?> = atomic(null)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index] != key) { // optimize for successful lookup
                if (map[index] == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.length()
                index -= 2
            }
            // found key -- return value
            return map[index + 1]
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0

            while (map[index] != key) { // optimize for successful lookup
                val currentKey = map[index]
                val currentValue = map[index + 1]

                if (isInvalidValue(currentValue))
                    return NEEDS_REHASH

                if (!isKeyPresented(currentKey)) {
                    // If key is not presented, and we want to remove something by this key,
                    // we don't need to do anything, because everything already in a valid state
                    if (isRemoveOperation(value))
                        return NULL_VALUE

                    // Claiming the slot for our putting valid value
                    map[index] = key
                    break
                } else {
                    // If the key was presented, and we're succeeded with its value changing,
                    // everything is done, so we can just return with the previous value
                    if (map.compareAndSet(index + 1, currentValue, value))
                        return currentValue
                }

                if (++probes >= MAX_PROBES)
                    return NEEDS_REHASH

                // Cycling the hash table
                if (index == 0)
                    index = map.length()

                index -= 2
            }

            // The key was found, so we could perform the setting of the new value
            return map.getAndSet(index + 1, value)
        }

        fun rehash(): Core {
            val newCore = Core(map.length()) // map.length is twice the current capacity
            var index = 0
            while (index < map.length()) {
                if (isValue(map[index + 1])) {
                    val result = newCore.putInternal(map[index], map[index + 1])
                    assert(result == 0) { "Unexpected result during rehash: $result" }
                }
                index += 2
            }
            return newCore
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        private fun isKeyPresented(key: Int): Boolean = key != NULL_KEY
        private fun isRemoveOperation(value: Int): Boolean = value == DEL_VALUE
        private fun isInvalidValue(value: Int): Boolean = value < 0 // Value is invalid for reading purposes, but can indicate the state of hash table
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