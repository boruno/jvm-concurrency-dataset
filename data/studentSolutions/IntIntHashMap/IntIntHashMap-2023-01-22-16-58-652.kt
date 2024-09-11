import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
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
        while (true) {
            val oldCore = core.value
            val oldValue = oldCore.getInternal(key)
            if (oldValue != NEEDS_REHASH) return toValue(oldValue)
            val newCore = oldCore.rehash()
            core.compareAndSet(oldCore, newCore)
        }
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
            val oldCore = core.value
            val oldValue = oldCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            val newCore = oldCore.rehash()
            core.compareAndSet(oldCore, newCore)
        }
    }

    private class Core internal constructor(val capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val next: AtomicRef<Core?> = atomic(null)
        val map: AtomicIntArray = AtomicIntArray(2 * capacity)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var currentCore = this
            while (true) {
                var index = currentCore.index(key)
                var probes = 0
                var expectedKey = currentCore.map[index].value
                var expectedValue = currentCore.map[index + 1].value
                while (expectedKey != key) { // optimize for successful lookup
                    if (expectedKey == NULL_KEY) return NULL_VALUE // not found -- no value
                    if (++probes >= MAX_PROBES) return NULL_VALUE
                    if (index == 0) index = currentCore.map.size
                    index -= 2
                    expectedKey = currentCore.map[index].value
                    expectedValue = currentCore.map[index + 1].value
                }
                // found key -- return value
                if (isValue(expectedValue) || expectedValue == NULL_VALUE || expectedValue == DEL_VALUE) {
                    return expectedValue
                } else {
                    return NEEDS_REHASH
                }
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            val currentCore = this
            var index = index(key)
            var probes = 0
            while (true) { // optimize for successful lookup
                val expectedKey = currentCore.map[index].value
                val expectedValue = currentCore.map[index + 1].value

                // если значение нам непонятно - идем на рехэш
                if (expectedValue == S_MARK || isVMod(expectedValue)) {
                    return NEEDS_REHASH
                }

                if (expectedKey == NULL_KEY) {
                    if (value == DEL_VALUE) return NULL_VALUE
                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        if (currentCore.map[index + 1].compareAndSet(expectedValue, value)) {
                            // базовый случай - все прошло хорошо
                            return expectedValue
                        } else {
                            continue  // если не получилось, значит уже рехешировали, посмотрим на другом круге
                        }
                    }
                } else if (expectedKey == key) {
                    if (currentCore.map[index + 1].compareAndSet(expectedValue, value)) {
                        return expectedValue
                    } else {
                        continue // если мы тут, кто-то успел записать другое значение, значит пойдем на второй круг
                        // и обновим значение
                    }
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = currentCore.map.size
                index -= 2
            }
        }

        fun putInternalOnRehash(key: Int, value: Int) {
            assert(value != DEL_VALUE)
            var index = index(key)
            while (true) {
                val expectedKey = map[index].value
                val expectedValue = map[index + 1].value
                if (expectedKey == NULL_KEY) {
                    if (map[index].compareAndSet(NULL_KEY, key)) {
                        if (expectedValue == NULL_VALUE) { // если в новой мапе был 0 - обновим его
                            if (map[index + 1].compareAndSet(expectedValue, value)) {
                                return
                            } else {
                                if (map[index + 1].value != value) {
                                    throw IllegalStateException("")
                                }
                            }
                        }
                    } else {
                        if (map[index].value == key) {
                            continue // уже обновили ключ, нужно обновить значение
                        }
                        // не нулл кей
                    }
                } else if (expectedKey == key) {
                    if (map[index + 1].compareAndSet(expectedValue, value)) {
                        return
                    }
                }
                if (index == 0) index = map.size
                index -= 2
            }
        }

        fun rehash(): Core {
            var newCore = Core(map.size) // map.length is twice the current capacity
            if (!next.compareAndSet(null, newCore)) {
                newCore = next.value!!
            }
            var index = 0
            while (index < map.size) {
                val expectedKey = map[index].value
                val expectedValue = map[index + 1].value
                if (expectedValue == S_MARK) {
                    //pass
                } else if (expectedKey == NULL_KEY || expectedValue == NULL_VALUE || expectedValue == DEL_VALUE) {
                    if (!map[index + 1].compareAndSet(expectedValue, S_MARK)) {
                        continue
                    }
                } else if (isValue(expectedValue)) {
                    if (!map[index + 1].compareAndSet(expectedValue, expectedValue * -1)) {
                        continue
                    }
                    newCore.putInternalOnRehash(expectedKey, expectedValue)
                    map[index + 1].compareAndSet(expectedValue * -1, S_MARK)
                } else if (isVMod(expectedValue)) {
                    newCore.putInternalOnRehash(expectedKey, -1 * expectedValue)
                    map[index + 1].compareAndSet(expectedValue, S_MARK)
                }

                index += 2
            }
            return newCore
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index1(key: Int): Int = (key * MAGIC ushr shift) * 2
        fun index(key: Int): Int = (key % capacity) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val S_MARK = Int.MIN_VALUE // mark as transferred
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)
private fun isVMod(value: Int): Boolean = value in (S_MARK + 1) until 0

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0