import kotlinx.atomicfu.AtomicIntArray
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
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue

            core.compareAndSet(curCore, curCore.rehash())
        }
    }

    private class Core constructor(capacity: Int) {
        val next = atomic<Core?>(null)

        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            var curKey = map[index].value
            while (curKey != key) { // optimize for successful lookup
                if (curKey == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
                curKey = map[index].value
            }
            // found key -- return value
            val value = map[index + 1].value
            return if (value == MOVED_VALUE) next.value!!.getInternal(key) else value // maybe isFixed check
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            external@while (true) {
                var probes = 0
                var curKey = map[index].value
                while (curKey != key) { // optimize for successful lookup
                    if (curKey == NULL_KEY) {
                        if (value == DEL_VALUE) return NULL_VALUE
                        if (map[index].compareAndSet(curKey, key)) {
                            break
                        } else {
                            continue@external
                        }
                    }
                    if (++probes >= MAX_PROBES) return NEEDS_REHASH
                    if (index == 0) index = map.size
                    index -= 2
                    curKey = map[index].value
                }
                val oldValue = map[index + 1].value
                if (oldValue == MOVED_VALUE || isFixed(oldValue)) {
//                    return next.value!!.putInternal(key, value)
                    return NEEDS_REHASH
                }
                if (map[index + 1].compareAndSet(oldValue, value)) return oldValue
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size))
            val newCore = next.value!!
            var index = 0
            var value: Int
            while (index < map.size) {
                while(true) {
                    value = map[index + 1].value
                    if (isValue(value)) {
                        val fixedValue = fix(value)
                        if (!map[index + 1].compareAndSet(value, fixedValue)) continue
                        newCore.putInternal(map[index].value, value)
                        map[index + 1].compareAndSet(fixedValue, MOVED_VALUE)
                    }
                    if (isFixed(value)) {
                        newCore.putInternal(map[index].value, unfix(value))
                        map[index + 1].compareAndSet(value, MOVED_VALUE)
                    }
                    if (value == NULL_VALUE || value == DEL_VALUE) {
                        if (!map[index + 1].compareAndSet(value, MOVED_VALUE)) continue
                    }
                    break
                }
                index += 2
            }
            return newCore
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
private const val MOVED_VALUE = Int.MIN_VALUE // mark for moved value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

private fun isFixed(value: Int): Boolean = value < 0 && value != Int.MIN_VALUE
private fun fix(value: Int): Int = Int.MIN_VALUE + value
private fun unfix(value: Int): Int = value and Int.MAX_VALUE

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int {
    return if (isValue(value)) {
        value
    } else {
        if (isFixed(value)) unfix(value) else 0
    }
}