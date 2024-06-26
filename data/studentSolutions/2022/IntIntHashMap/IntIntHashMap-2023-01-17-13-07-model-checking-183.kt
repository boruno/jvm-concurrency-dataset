import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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
        //println("doGet")
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
        //println("doPut")
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
        //println("doRem")
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val cor = core.value;
            val oldValue = cor.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) {
                return oldValue
            }
            core.compareAndSet(cor, cor.rehash())
        }
    }

    private class Core(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = atomicArrayOfNulls<Int>(2 * capacity)
        val nextCore = atomic<Core?>(null)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            val index = getInternalIndex(key)
            if (index == NEEDS_REHASH) {
                return NULL_VALUE
            }
            val res = map[index + 1].value ?: 0
            if (res == MOVED_VALUE) {
                return nextCore.value!!.getInternal(res)
            }
            if (res < 0) {
                return -res
            }
            return res
        }

        fun getInternalIndex(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val mkv = map[index].value
                if (mkv == key) {
                    break
                }
                if (mkv == null) {
                    // not found -- claim this slot
                    //if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (map[index].compareAndSet(null, key)) {
                        break
                    } else {
                        continue
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            return index;
        }

        fun putInternal(key: Int, value: Int): Int {
            val index = getInternalIndex(key)
            if (index == NEEDS_REHASH) {
                return NEEDS_REHASH
            }
            while (true) {
                val prev = map[index + 1].value
                if (prev == MOVED_VALUE) {
                    return nextCore.value!!.putInternal(key, value)
                }
                if (prev != null && prev < 0) {
                    val res = nextCore.value!!.putInternal(key, value)
                    if (res == NULL_VALUE) {
                        return -prev
                    }
                    return res
                }
                assert(prev == null || prev >= 0)
                if (!map[index + 1].compareAndSet(prev, value)) {
                    continue
                }
                return prev ?: NULL_VALUE
            }
        }

        fun rehash(): Core {
            nextCore.compareAndSet(null, Core(map.size)) // map.length is twice the current capacity
            val newCore = nextCore.value!!
            var index = 0
            while (index < map.size) {
                val v = map[index + 1].value
                if (v != null && v > 0) {
                    if (!map[index + 1].compareAndSet(v, -v)) {
                        continue
                    }
                    val k = map[index].value!!
                    val z = newCore.getInternalIndex(k)
                    newCore.map[z + 1].compareAndSet(null, v)
                    val jr = map[index + 1].getAndSet(MOVED_VALUE)
                    if (jr != -v) {
                        println(jr)
                        TODO("fucking hell")
                    }
                    /*if (!map[index + 1].compareAndSet(-v, MOVED_VALUE)) {
                        val W = map[index + 1].value
                        if (!map[index + 1].compareAndSet(-v, MOVED_VALUE)) {
                            println(">>" + W + " " + v + " " + -v + " " + (W == -v))
                            TODO("this should never happen")
                        }
                    }*/
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
private const val MOVED_VALUE = Int.MIN_VALUE
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0