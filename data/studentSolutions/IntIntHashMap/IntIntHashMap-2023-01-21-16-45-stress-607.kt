import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic

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
            rehash(curCore)
        }
    }

    private fun rehash(curCore: Core) {
        curCore.rehash()
        moveCore()
    }

    private fun moveCore() {
        while (true) {
            val curCore = core.value
            val next = curCore.next.value
            if (next == null || !next.finalized) return
            core.compareAndSet(curCore, next)
        }
    }


    private class Core(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2 * capacity)
        val next = atomic<Core?>(null)
        @Volatile
        var finalized = false
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var keyIndex = index(key)
            var probes = 0

            while (true) {
                return when (getKey(keyIndex)) {
                    key -> {
                        val value = getValue(valueIndex(keyIndex))
                        if (value < 0) {
                            val result = next.value?.getInternal(key) ?: error("No next core")
                            if (value != MOVED && result == NULL_VALUE) {
                                value
                            } else {
                                result
                            }
                        } else {
                            value
                        }
                    }
                    NULL_KEY -> NULL_VALUE
                    else -> if (++probes >= MAX_PROBES) {
                        NULL_VALUE
                    } else {
                        if (keyIndex == 0) keyIndex = map.size
                        keyIndex -= 2
                        continue
                    }
                }
            }
        }

        fun putInternal(key: Int, value: Int, move: Boolean = false): Int {
            assert(isValue(value) || value == DEL_VALUE) { "Invalid value: $value" }

            var keyIndex = index(key)
            var valueIndex: Int
            var probes = 0

            while (true) {
                valueIndex = valueIndex(keyIndex)

                when (getKey(keyIndex)) {
                    key -> break
                    NULL_KEY ->
                        if (cas(keyIndex, NULL_KEY, key)
                            || getValue(valueIndex) == MOVED && getKey(keyIndex) == NULL_KEY)
                            break

                    else -> if (++probes >= MAX_PROBES) {
                        return if (value == DEL_VALUE) NULL_VALUE else NEEDS_REHASH
                    } else {
                        if (keyIndex == 0) keyIndex = map.size
                        keyIndex -= 2
                    }
                }
            }

            val curKey = getKey(keyIndex)

            if (move) {
                cas(valueIndex, NULL_VALUE, value)
                return NULL_VALUE
            }

            assert(curKey == key) { "Key mismatch: $keyIndex, $key" }

            while (true) {
                val oldValue = getValue(valueIndex)

                when {
                    oldValue == MOVED -> return next.value?.putInternal(key, value) ?: error("No next core")

                    oldValue < 0 -> {
                        val result = next.value?.putInternal(key, value) ?: error("No next core")

                        return if (result == NULL_VALUE) {
                            oldValue
                        } else {
                            result
                        }
                    }

                    cas(valueIndex, oldValue, value) -> return oldValue
                }
            }
        }

        fun rehash() {
            next.compareAndSet(null, Core(map.size)) // map.length is twice the current capacity

            val newCore = next.value ?: error("Next core is null")

            var keyIndex = 0
            while (keyIndex < map.size) {
                val valueIndex = valueIndex(keyIndex)
                val key = getKey(keyIndex)
                val value = getValue(valueIndex)

                if (isValue(value)) {
                    if (!cas(valueIndex, value, -value)) continue
                    newCore.putInternal(key, value, move = true)
                    cas(valueIndex, -value, MOVED)
                } else if (value == NULL_VALUE || value == DEL_VALUE) {
                    if (!cas(valueIndex, value, MOVED)) continue
                } else if (value != MOVED) {
                    assert(value < 0) { "Invalid value: $value" }

                    newCore.putInternal(key, -value, move = true)
                    cas(valueIndex, value, MOVED)
                }

                assert(getValue(valueIndex) == MOVED) { "Value should be MOVED" }

                keyIndex += 2
            }

            newCore.finalized = true
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        private fun cas(index: Int, expect: Int, update: Int): Boolean = map[index].compareAndSet(expect, update)

        private fun valueIndex(keyIndex: Int): Int {
            assert(keyIndex % 2 == 0) { "Key index must be even: $keyIndex" }
            return keyIndex + 1
        }

        private fun getKey(keyIndex: Int): Int {
            assert(keyIndex % 2 == 0) { "Key index must be even: $keyIndex" }
            return map[keyIndex].value
        }

        private fun getValue(valueIndex: Int): Int {
            assert(valueIndex % 2 == 1) { "Value index must be odd: $valueIndex" }

            val value = map[valueIndex].value

            assert(value != NEEDS_REHASH) { "Value must not be NEEDS_REHASH" }

            return value
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = Int.MIN_VALUE // returned by `putInternal` to indicate that rehash is needed
private const val MOVED = Int.MIN_VALUE + 1 // mark for moved value

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = when {
    value == MOVED -> error("Value was moved")
    isValue(value) -> value
    value < 0 -> -value
    else -> 0
}
