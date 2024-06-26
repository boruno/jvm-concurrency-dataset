import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.reflect.jvm.internal.impl.util.ReturnsCheck.ReturnsUnit

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
            val oldValue = core.value.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            core.compareAndSet(core.value, core.value.rehash())
        }
    }

    private class Core internal constructor(capacity: Int) {
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
            while (true) {
                var probes = 0
                while (map[index].value != key) { // optimize for successful lookup
                    if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                    if (++probes >= MAX_PROBES) return NULL_VALUE
                    if (index == 0) index = map.size
                    index -= 2
                }
                // found key -- return value
                val value = map[index + 1].value
                val nextCore = next.value
                return when (value) {
                    MOVED_VALUE -> if (nextCore !== null) nextCore.getInternal(
                        key
                    ) else throw IllegalArgumentException("Something wrong")

                    in MOVED_VALUE + 1 until 0 -> {
                        val fixedValue = -value
                        if (nextCore !== null) {
                            nextCore.move(map[index].value, fixedValue)
                            val result = nextCore.getInternal(key)
                            map[index + 1].compareAndSet(value, MOVED_VALUE)
                            result
                        } else throw IllegalArgumentException("Something wrong")
                    }

                    else -> value
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            while (true) {
                var probes = 0
                while (map[index].value != key) { // optimize for successful lookup
                    if (map[index].value == NULL_KEY) {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                        map[index].compareAndSet(NULL_KEY, key)
                        map[index + 1].compareAndSet(map[index + 1].value, value)
                        return map[index + 1].value
                    }
                    if (++probes >= MAX_PROBES) return NEEDS_REHASH
                    if (index == 0) index = map.size
                    index -= 2
                }
                // found key -- update value
                val oldValue = map[index + 1].value
                val nextCore = next.value
                return when (oldValue) {
                    MOVED_VALUE -> if (nextCore !== null) nextCore.putInternal(
                        key,
                        value
                    ) else throw IllegalArgumentException("Something wrong")

                    in MOVED_VALUE + 1 until 0 -> {
                        val fixedValue = -value
                        if (nextCore !== null) {
                            nextCore.move(map[index].value, fixedValue)
                            val result = nextCore.putInternal(key, value)
                            map[index + 1].compareAndSet(value, MOVED_VALUE)
                            result
                        } else throw IllegalArgumentException("Something wrong")
                    }

                    else -> if (map[index + 1].compareAndSet(oldValue, value)) oldValue else continue
                }
            }
        }

        fun rehash(): Core {
            val newCore = Core(map.size) // map.length is twice the current capacity
            var index = 0
            while (index < map.size) {
                if (isValue(map[index + 1].value)) {
                    val result = newCore.putInternal(map[index].value, map[index + 1].value)
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

        private fun move(key: Int, value: Int) {
            var index = index(key)
            var probes = 0
            while (true) {
                if (map[index].value == NULL_KEY) {
                    map[index].compareAndSet(NULL_KEY, key)
                    map[index + 1].compareAndSet(NULL_VALUE, key)
                    return
                }
                if (key == map[index].value) {
                    map[index + 1].compareAndSet(NULL_VALUE, value)
                    return
                }
                if (++probes >= MAX_PROBES) return
                if (index == 0) index = map.size
                index -= 2
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
private const val MOVED_VALUE = Int.MIN_VALUE
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0