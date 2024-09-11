import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.atomic.AtomicIntegerArray

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
        //if (key.equals(6) && )
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        //var realResult = -1
        while (true) {
            val currentCore = core.value
            val r = currentCore.putInternal(key, value)
            //if (/*realResult == -1 && */r >= 0) {
            //    realResult = r
            //}
            if (r == NEEDS_REHASH || r == VALUE_MOVED) {
                core.compareAndSet(currentCore, currentCore.rehash())
                continue
            } else if (r == RACE_CONDITION) {
                continue
            } else {
//                if (currentCore.REHASH_TRIGGERED) {
//                    continue
//                }
//                if (currentCore != core.value && core.value.getInternal(key) != value) {
//                    continue
//                }
                assert( r >= 0) { "heh " +  r}
                return r
                //return realResult
            }
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity) // arrayOfNulls<AtomicInt>(2 * capacity)//AtomicIntegerArray(2 * capacity) //Array(2 * capacity, { i -> atomic(0) })
        val shift: Int
        val next = atomic<Core?>(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            var v = map[index].value!!
            while (v != key) { // optimize for successful lookup
                if (v == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
                v = map[index].value
            }
            // found key -- return value
            return map[index + 1].value!!
        }

        fun putInternal(key: Int, value: Int): Int {
//            if (REHASH_TRIGGERED) {
//                return NEEDS_REHASH
//            }
            var index = index(key)
            var probes = 0
            var v = map[index].value
            var oldValue = map[index + 1].value

            while (v != key) { // optimize for successful lookup
                // IF no records with key are found
                // if normal : claim & write
                // if delete : do nothing
                if (v == NULL_KEY) {
                    if (value == DEL_VALUE) return NULL_VALUE
                    if (!map[index].compareAndSet(v, key))  {
                        return RACE_CONDITION
                    }
                    break
                }
                // ELSE continuation: searching record for key
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
                v = map[index].value
                oldValue = map[index + 1].value
            }

            // found key is found
            // gotta write value
            if (oldValue == REHASHED) {
                return VALUE_MOVED
            }
            if (!map[index + 1].compareAndSet(oldValue, value)){
                if (oldValue == REHASHED) {
                    return VALUE_MOVED
                }
                return RACE_CONDITION
            }
            return oldValue
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size)) // map.length is twice the current capacity
            var index = 0
            while (index < map.size) {
                if (isValue(map[index + 1].value)) {
                    var value = map[index + 1].value
                    while (!map[index + 1].compareAndSet(value, REHASHED)) {
                        value = map[index + 1].value
                    }
                    val result = next.value!!.putInternal(map[index].value, value)
                    assert(result == 0) { "Unexpected result during rehash: $result" }
                }
                index += 2
            }
            return next.value!!
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val REHASHED = -64
private const val VALUE_MOVED = -100
private const val RACE_CONDITION = -128

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