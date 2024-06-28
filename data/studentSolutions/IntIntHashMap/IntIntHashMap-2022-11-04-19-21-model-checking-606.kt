import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.math.abs

/**
 * Int-to-Int hash map with open addressing and linear probes.
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
        return toValue(getAndRehashWhileNeeded(key))
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
            val oldValue = core.value.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            rehash()
        }
    }

    private fun getAndRehashWhileNeeded(key: Int): Int {
        while (true) {
            val oldValue = core.value.getInternal(key)
            if (oldValue != NEEDS_REHASH) return oldValue
            rehash()
        }
    }

    private fun rehash() {
        val curCore = core.value
        curCore.rehashToNext()
        core.compareAndSet(curCore, curCore.next.value!!)
    }

    private class Core internal constructor(val capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            val value = map[index + 1].value
            if (value == MOVED_VALUE) {
                return next.value!!.getInternal(key)
            }
            // found key -- return value even if it fixed
            return abs(value)
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    map[index].value = key
                    break
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- try update value
            while (true) {
                val oldValue = map[index + 1].value
                if (oldValue == MOVED_VALUE || oldValue < 0) {
                    return next.value!!.putInternal(key, value)
                }
                if (map[index + 1].compareAndSet(oldValue, value)) {
                    return oldValue
                }
            }
        }

        fun rehashToNext() {
            if (next.value == null) {
                next.compareAndSet(null, Core(4 * capacity))
            }
            val nextCore = next.value!!
            for (i in (0 until map.size).step(2)) {
                while (true) {
                    val curKey = map[i].value
                    val curValue = map[i + 1].value
                    val success = if (curValue == NULL_VALUE) {
                        map[i + 1].compareAndSet(NULL_VALUE, MOVED_VALUE)
                    } else if (curValue == DEL_VALUE) {
                        map[i + 1].compareAndSet(DEL_VALUE, MOVED_VALUE)
                    } else if (curValue == MOVED_VALUE) {
                        true
                    } else {
                        kotlin.run {
                            if (curValue > 0) {
                                if (!map[i + 1].compareAndSet(curValue, -curValue)) {
                                    return@run false
                                }
                            }
                            nextCore.putInternal(curKey, abs(curValue))
                            true
                        }
                    }
                    if (success) {
                        break
                    }
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
private const val MOVED_VALUE = Int.MIN_VALUE // mark for moved value
private const val NEEDS_REHASH = -1 // returned to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0