import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.abs

/**
 * Int-to-Int hash map with open addressing and linear probes.
 */
class IntIntHashMap {
    private val core: AtomicRef<Core> = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }

        val currentCore = core.value
        return toValue(currentCore.getInternal(key))
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

        val previousValue = putAndRehashWhileNeeded(key, value)
        return toValue(previousValue)
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

        val previousValue = putAndRehashWhileNeeded(key, DEL_VALUE)
        return toValue(previousValue)
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val currentCore = core.value
            val oldValue = currentCore.putInternal(key, value)

            if (oldValue != NEEDS_REHASH)
                return oldValue

            val newCore = currentCore.rehash()
            core.compareAndSet(currentCore, newCore)
        }
    }

    override fun toString(): String {
        return core.toString()
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map: AtomicIntegerArray = AtomicIntegerArray(2 * capacity)
        val next: AtomicRef<Core?> = atomic(null)
        val shift: Int

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index] != key) { // optimize for successful lookup
                if (map[index] == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.length()
                index -= 2
            }
            // found key -- return value
            return map[index + 1]
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) { // optimize for successful lookup
                val currentKey = map[index]
                val currentValue = map[index + 1]

                if (isInvalidValue(currentValue))
                    return NEEDS_REHASH

                if (!isKeyPresented(currentKey)) {
                    // If key is not presented, and we want to remove something by this key,
                    // we don't need to do anything, because everything already in a valid state
                    if (isRemoveOperation(value))
                        return NULL_VALUE

                    // Claiming the slot for our putting valid value
                    map[index] = key

                    if (!map.compareAndSet(index + 1, currentValue, value))
                        return NEEDS_REHASH

                    return currentValue
                } else if (currentKey == key) {
                    // If the key was presented, and we're succeeded with its value changing,
                    // everything is done, so we can just return with the previous value
                    if (map.compareAndSet(index + 1, currentValue, value))
                        return currentValue
                }

                if (++probes >= MAX_PROBES)
                    return NEEDS_REHASH

                // Cycling the hash table
                if (index == 0)
                    index = map.length()

                index -= 2
            }
        }

        fun rehash(): Core {
            var newCore = Core(map.length()) // map.length is twice the current capacity

            // If we failed with setting the next core,
            // someone else has been already done it, so just take the next core and return it
            if (!next.compareAndSet(null, newCore))
                newCore = next.value!!

            var index = 0
            while (index < map.length()) {
                val key = map[index]
                val value = map[index + 1]
                if (isValue(value)) {
                    // If someone is already in process of rehashing, let it do it
                    if (!tryMarkValueAsMoving(valueIndex = index + 1, value = value))
                        continue

                    copyValue(newCore, key, value)

                    // Move has been performed, marking value in an old map as moved
                    tryMarkValueAsMoved(valueIndex = index + 1, value = value)
                } else if (isValueMoving(value)) {
                    // We should help to move the value in case if primary thread was switched for a long time
                    copyValue(newCore, key, value)

                    // Move has been performed, marking value in an old map as moved
                    tryMarkValueAsMoved(valueIndex = index + 1, value = value)
                } else if (value == NULL_VALUE) {
                    // Possibly key was already inserted, but the value was not. So we should anyway break this value-cell.
                    if (!tryMarkValueAsMoved(valueIndex = index + 1, value = value))
                        continue
                }

                index += 2
            }

            return newCore
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2

        private fun isKeyPresented(key: Int): Boolean = key != NULL_KEY
        private fun isRemoveOperation(value: Int): Boolean = value == DEL_VALUE
        private fun isInvalidValue(value: Int): Boolean = value < 0 // Value is invalid for reading purposes, but can indicate the state of hash table
        private fun isValueMoved(value: Int): Boolean = value == MOVED_VALUE
        private fun isValueMoving(value: Int): Boolean = isInvalidValue(value) && !isValueMoved(value)
        private fun tryMarkValueAsMoving(valueIndex: Int, value: Int): Boolean {
            return map.compareAndSet(valueIndex, value, -1 * value)
        }

        private fun tryMarkValueAsMoved(valueIndex: Int, value: Int): Boolean {
            val expectedValue = if (value == NULL_VALUE) 0 else -1 * abs(value)
            return map.compareAndSet(valueIndex, expectedValue, MOVED_VALUE)
        }

        private fun copyValue(core: Core, key: Int, value: Int) {
            // We can not use 'putInternal' method because of the situation when one thread
            // while helping another switched thread already copied the value
            // and then the switched thread returns and tries to copy value in the cell with already copied value,
            // so 'putInternal' returns not 0 but the value it was trying to put.

            val copyingValue = abs(value)
            var index = core.index(key)

            while (true) {
                val currentKey = core.map[index]

                if (currentKey == NULL_KEY) {
                    if (!core.map.compareAndSet(index, NULL_KEY, key)) {
                        // Maybe someone has already inserted our key, so we can try to work with it
                        // in the next iteration. Otherwise, we should find a better index for our key-value pair.
                        val updatedKey = core.map[index]
                        if (updatedKey == key)
                            continue
                    } else {
                        // If we succeeded with the key insertion, everything is fine.
                        core.map.compareAndSet(index + 1, NULL_VALUE, copyingValue)
                        return
                    }
                } else if (currentKey == key) {
                    // We do not need to check the result of CAS, so if it's not 'NULL_VALUE' anymore,
                    // so everything is fine.
                    core.map.compareAndSet(index + 1, NULL_VALUE, copyingValue)
                    return
                }

                // Cycling the hash table
                if (index == 0)
                    index = map.length()

                index -= 2
            }
        }

        override fun toString(): String {
            return map.toString()
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val MOVED_VALUE = Int.MIN_VALUE // mark for moved value due to table rehashing
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0