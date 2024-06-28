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
    public val core: AtomicRef<Core?> = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.value!!.getInternal(key).toInt())
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
//        require(key > 0) { "Key must be positive: $key" }
//        require(isValue(value)) { "Invalid value: $value" }
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

    fun putAndRehashWhileNeeded(key: Int, value: Long): Int {
        while (true) {
            val curCore = core.value
            val res = curCore!!.putInternal(key, value)
            if (res.second) {
                curCore.rehash()
                core.compareAndSet(curCore, curCore.nextCore.value)
                continue
            }
            if (res.first > 0 || res.first == NULL_VALUEI) return res.first
        }
    }

    class Core internal constructor(capacity: Int) {
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
//            if (curValue == TRUE_DEL_VALUE) return NULL_VALUE
            if (curValue == NULL_VALUE) return NULL_VALUE
            if (curValue == DEL_VALUE) return NULL_VALUE
            // TODO: мб ловушка на TRUE_DEL_VALUE
            if (isMovedValue(curValue) || curValue == TRUE_DEL_VALUE) {
                val res = nextCore.value!!.getInternal(key)
                if (res > 0) return res
                if (res == NULL_VALUE) return fromMoved(curValue)
                if (res == DEL_VALUE) return NULL_VALUE
                @Suppress("KotlinConstantConditions")
                if (res < 0) return fromMoved(res)
            }
            return curValue
        }

        fun putRehash(key: Int, value: Long): Pair<Int, Boolean> {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val curKey = map[index].value
                if (curKey == NULL_KEY) {
                    if(map[index].compareAndSet(NULL_KEY, key.toLong())) {
                        map[index + 1].compareAndSet(NULL_VALUE, value)
                        return Pair(0, false)
                    } else {
                        continue
                    }
                } else if (curKey == key.toLong()) {
                    map[index + 1].compareAndSet(NULL_VALUE, value)
                    return Pair(0, false)
                }
                if (++probes >= MAX_PROBES) return Pair(0, value != DEL_VALUE)
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun putInternal(key: Int, value: Long): Pair<Int, Boolean> {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val curKey = map[index].value
                if (curKey == NULL_KEY) {
                    val curValue = map[index + 1].value
                    // not found -- claim this slot
                    if(isMovedValue(curValue) || curValue == TRUE_DEL_VALUE) return Pair(0, true)
                    if(value == DEL_VALUE) return Pair(NULL_VALUEI, false) // remove of missing item, no need to claim slot
                    if(map[index].compareAndSet(NULL_KEY, key.toLong())) {
                        if(!map[index + 1].compareAndSet(curValue, value)) {
                            continue
                        }
                        return Pair(curValue.toInt(), false)
                    } else {
                        continue
                    }
                }
                if (curKey == key.toLong()) {
                    val curValue = map[index + 1].value
                    if((curValue == DEL_VALUE || curValue == NULL_VALUE) && value == DEL_VALUE) {
                        return Pair(NULL_VALUEI, false)
                    }
                    if(isMovedValue(curValue) || curValue == TRUE_DEL_VALUE) {
                        return Pair(0, true)
                    }
                    if(map[index + 1].compareAndSet(curValue, value)) {
                        if (curValue == DEL_VALUE) {
                            return Pair(0, false)
                        }
                        return Pair(curValue.toInt(), false)
                    } else {
                        continue
                    }
                }
                if (++probes >= MAX_PROBES) return Pair(0, value != DEL_VALUE)
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun rehash(): Core {
            nextCore.compareAndSet(null, Core(map.size))
            val newCore = nextCore.value!! // map.length is twice the current capacity
            var index = 0
            while (index < map.size) {
                val curKey = map[index].value
                val curValue = map[index + 1].value
                if (curValue == NULL_VALUE || curValue == DEL_VALUE) {
                    if(!map[index + 1].compareAndSet(curValue, TRUE_DEL_VALUE)) continue
                } else if(isMovedValue(curValue)) {
                    newCore.putRehash(curKey.toInt(), fromMoved(curValue))
                    map[index + 1].compareAndSet(fromMoved(curValue), TRUE_DEL_VALUE)
//                    map[index + 1].compareAndSet()
                } else if (isValue(curValue.toInt())) {
                    if (!map[index + 1].compareAndSet(curValue, mkMovedValue(curValue.toInt()))) {
                        continue
                    }
                    newCore.putRehash(curKey.toInt(), curValue)
                    map[index + 1].compareAndSet(mkMovedValue(curValue.toInt()), TRUE_DEL_VALUE)
//                    assert(result.first == 0) { "Unexpected result during rehash: $result" }
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
private const val TRUE_DEL_VALUE: Long = -(Int.MAX_VALUE.toLong()) - 1 // mark for truely removed value

private fun isMovedValue(value: Long): Boolean = value in (DEL_VALUE until 0)
private fun mkMovedValue(value: Int): Long = value.toLong() * -1
private fun fromMoved(value: Long): Long = (value * -1)

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until Int.MAX_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0