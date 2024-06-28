import kotlinx.atomicfu.AtomicLongArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private var core: Core? = Core(INITIAL_CAPACITY)

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core!!.getInternal(key).toInt())
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
        return toValue(putAndRehashWhileNeeded(key, value.toLong()))
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
        return putAndRehashWhileNeeded(key, DEL_VALUE)
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Long): Int {
        while (true) {
            val res = core!!.putInternal(key, value)
            if (!res.second) return res.first
            core = core!!.rehash()
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicLongArray(2 * capacity)
        val shift: Int
        val nextCore: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(intKey: Int): Long {
            var index = index(intKey)
            var probes = 0
            val key = intKey.toLong()
            while (true) { // optimize for successful lookup
                val curKey = map[index].value
                if (curKey == NULL_KEY) return NULL_VALUE // not found -- no value
                if (curKey == key) return getValue(index, intKey)
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun getValue(index: Int, key: Int): Long {
            val curValue = map[index + 1].value
            if (curValue == NULL_VALUE) return NULL_VALUE
            if (curValue == DEL_VALUE) return NULL_VALUE
            if (curValue < 0) {
                val res = nextCore.value!!.getInternal(key)
                if (res > 0) return res
                if (res == NULL_VALUE) return fromMoved(curValue)
                if (res == DEL_VALUE) return NULL_VALUE
                @Suppress("KotlinConstantConditions")
                if (res < 0) return fromMoved(res)
            }
            return curValue
        }

        fun putInternal(key: Int, value: Long): Pair<Int, Boolean> {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val curKey = map[index].value
                if (curKey == NULL_KEY) {
                    val curValue = map[index + 1].value
                    // not found -- claim this slot
                    if(isMovedValue(curValue)) return Pair(0, true)
                    if(value == DEL_VALUE) return Pair(NULL_VALUEI, false) // remove of missing item, no need to claim slot
                    if(map[index].compareAndSet(NULL_KEY, key.toLong())) {
                        map[index + 1].compareAndSet(curValue, value)
                        return Pair(curValue.toInt(), false)
                    } else {
                        continue
                    }
                }
                if (curKey == key.toLong()) {
                    val curValue = map[index + 1].value
                    if(map[index + 1].compareAndSet(curValue, value)) {
                        return Pair(curValue.toInt(), false)
                    } else {
                        continue
                    }
                }
                if (++probes >= MAX_PROBES) return Pair(0, true)
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun rehash(): Core {
            val newCore = Core(map.size * 2) // map.length is twice the current capacity
            nextCore.compareAndSet(null, newCore)
            var index = 0
            while (index < map.size) {
                val curKey = map[index].value
                val curValue = map[index + 1].value
                if (isValue(curValue.toInt())) {
                    val result = newCore.putInternal(curKey.toInt(), curValue)
                    assert(result.first == 0) { "Unexpected result during rehash: $result" }
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
private const val NULL_KEY: Long = 0 // missing key (initial value)
private const val NULL_KEYI: Int = 0 // missing key (initial value)
private const val NULL_VALUEI: Int = 0 // missing value (initial value)
private const val NULL_VALUE: Long = 0 // missing value (initial value)
private const val DEL_VALUE: Long = -(Int.MAX_VALUE.toLong()) // mark for removed value

private fun isMovedValue(value: Long): Boolean = value in (DEL_VALUE until 0)
private fun mkMovedValue(value: Int): Long = value.toLong() * -1
private fun fromMoved(value: Long): Long = (value * -1)

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until Int.MAX_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0