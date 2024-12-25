//package day4

import kotlinx.atomicfu.*
import java.util.logging.Level
import java.util.logging.Logger

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

    private class Core(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val next = atomic<Core?>(null)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while(true) {
                when (map[index].value) {
                    NULL_KEY -> return NULL_VALUE
                    key -> break
                    else -> {
                        if (++probes >= MAX_PROBES) return NULL_VALUE
                        if (index == 0) index = map.size
                        index -= 2
                        continue
                    }
                }
            }

            // found key -- find value
            val curValue = map[index + 1].value
            return when {
                curValue == TRANSFERRED_VALUE -> {
                    next.value!!.getInternal(key)
                }
                curValue < 0 -> {
                    -curValue
                }
                else -> {
                    curValue
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) {
                when (map[index].value) {
                    NULL_KEY -> {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                        if (map[index].compareAndSet(NULL_KEY, key)) break
                        else continue
                    }
                    key -> break
                    else -> {
                        if (++probes >= MAX_PROBES) return NEEDS_REHASH
                        if (index == 0) index = map.size
                        index -= 2
                        continue
                    }
                }
            }

            // found key -- update value
            while (true) {
                val oldValue = map[index + 1].value
                return when {
                    oldValue == TRANSFERRED_VALUE -> {
                        rehash()
                        continue
                        //next.value!!.putInternal(key, value)
                    }

                    oldValue < 0 -> {
                        rehash()
                        continue
                    }

                    else -> {
                        if (!map[index + 1].compareAndSet(oldValue, value)) continue
                        oldValue
                    }
                }
            }
        }

        fun rehash(): Core {
            val newCore = Core(map.size) // map.length is twice the current capacity
            next.compareAndSet(null, newCore)
            val nextCore = next.value!!
            var index = 0
            while (index < map.size) {
                val curKey = map[index].value
                val curValue = map[index+1].value
                when {
                    curValue == TRANSFERRED_VALUE -> {
                        break
                    }
                    curValue == 0 -> {
                        map[index + 1].compareAndSet(curValue, TRANSFERRED_VALUE)
                        continue
                    }
                    curValue > 0 -> {
                        map[index + 1].compareAndSet(curValue, -curValue)
                        continue
                    }
                    curValue < 0 -> {
                        var newIndex = nextCore.index(index)
                        while (true) {
                            when (nextCore.map[newIndex].value) {
                                NULL_KEY -> {
                                    // not found -- claim this slot
                                    (nextCore.map[newIndex].compareAndSet(NULL_KEY, curKey))
                                    if (newCore.map[newIndex].value == curKey) break
                                    else continue
                                }
                                curKey -> {
                                    break
                                }
                                else -> {
                                    if (newIndex == 0) newIndex = nextCore.map.size
                                    newIndex -= 2
                                    continue
                                }
                            }
                        }
                        nextCore.map[newIndex + 1].compareAndSet(NULL_VALUE, -curValue)

                        map[index+1].compareAndSet(curValue, TRANSFERRED_VALUE)
                        if (map[index+1].value == TRANSFERRED_VALUE) break

                        continue
                    }
                }
                index += 2
            }
            var state = ""
            (0 until map.size).forEach {i -> state = state + newCore.map[i].value + ", " }
            Logger.getLogger("core").log(Level.INFO, state)
            return nextCore
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
private const val TRANSFERRED_VALUE = Int.MIN_VALUE

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0