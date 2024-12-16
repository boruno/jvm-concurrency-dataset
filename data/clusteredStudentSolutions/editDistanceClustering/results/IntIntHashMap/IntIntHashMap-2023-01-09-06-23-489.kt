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
            val currentCore = core.value
            val oldValue = currentCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            currentCore.rehash()
            core.compareAndSet(currentCore, currentCore.next.value!!)
        }
    }

    private fun getAndRehashWhileNeeded(key: Int): Int {
        while (true) {
            val currentCore = core.value
            val value = currentCore.getInternal(key)
            if (value != NEEDS_REHASH) return value
            currentCore.rehash()
            core.compareAndSet(currentCore, currentCore.next.value!!)
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val outdated = atomic(false)
        val map = atomicArrayOfNulls<ArrayValue>(2 * capacity)
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
            while (true) {
                val value = map[index].value ?: return NULL_VALUE// optimize for successful lookup

                // not found -- no value

                if (value is Key) {
                    val innerKey = value.key
                    if (innerKey == key) break
                    if (++probes >= MAX_PROBES) return NULL_VALUE
                    if (index == 0) index = map.size
                    index -= 2
                }  else {
                    throw IllegalStateException()
                }
            }
            // found key -- return value

            return when (val value = map[index + 1].value) {
                is Value -> value.value
                is FixedValue -> value.value
                is OutDated -> NEEDS_REHASH
                is Key -> throw IllegalStateException()
                else -> NULL_KEY
            }
        }

        fun putInternal(key: Int, value: Int): Int {

                var index = index(key)
                var probes = 0
                while (true) { // optimize for successful lookup
                    val innerKey = map[index].value

                    if (innerKey == null && map[index + 1].value == null) {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                        if (map[index].compareAndSet(null, Key(key))) {
                            break
                        }
                    } else if (map[index + 1].value is OutDated || map[index + 1].value is FixedValue) {
                        return NEEDS_REHASH
                    }

                    if (innerKey is Key && innerKey.key == key) {
                        break
                    }

                    if (++probes >= MAX_PROBES) return NEEDS_REHASH
                    if (index == 0) index = map.size
                    index -= 2
                }

            while(true) {
                // found key -- update value
                val oldValue = map[index + 1].value
                if (oldValue is OutDated || oldValue is FixedValue) {
                    return NEEDS_REHASH
                }

                if (map[index + 1].compareAndSet(oldValue, Value(value))) {
                    return when (oldValue) {
                        is Value -> oldValue.value
                        else -> NULL_KEY
                    }
                }
            }
        }

        private fun putIfAbsent(key: Int, value: Int) {
            while(true) {
                var index = index(key)
                var probes = 0
                while (true) { // optimize for successful lookup
                    val innerKey = map[index].value
                    if (innerKey == null) {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return // remove of missing item, no need to claim slot
                        if (map[index].compareAndSet(null, Key(key))) {
                            break
                        }
                    }

                    if (innerKey is Key && innerKey.key == key) {
                        break
                    }

                    if (++probes >= MAX_PROBES) return
                    if (index == 0) index = map.size
                    index -= 2
                }

                // found key -- update value
                map[index + 1].compareAndSet(null, Value(value))
                return
            }
        }

        fun rehash(): Core {
            if (outdated.value) {
                return next.value!!
            }
            var newCore = Core(map.size) // map.length is twice the current capacity
            next.compareAndSet(null, newCore)
            newCore = next.value!!
            var index = map.size - 2
            while (index >= 0) {
                val key = map[index].value
                var value = map[index + 1].value
                if (value is Value) {
                    map[index + 1].compareAndSet(value, FixedValue(value))
                }
                value = map[index + 1].value
                if (key is Key && value is FixedValue && isValue(value.value)) {
                    newCore.putIfAbsent(key.key, value.value)
                }

                map[index + 1].value = OutDated()

                index -= 2
            }
            outdated.value = true
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
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

sealed class ArrayValue

class Key(val key: Int) : ArrayValue()
class Value(val value: Int) : ArrayValue() {

}

class FixedValue(value: Value) : ArrayValue() {
    val ref = value
    val value = value.value

}

class OutDated : ArrayValue()

//package mpp.dynamicarray
//
//import kotlinx.atomicfu.*
//
//interface DynamicArray<E> {
//    /**
//     * Returns the element located in the cell [index],
//     * or throws [IllegalArgumentException] if [index]
//     * exceeds the [size] of this array.
//     */
//    fun get(index: Int): E
//
//    /**
//     * Puts the specified [element] into the cell [index],
//     * or throws [IllegalArgumentException] if [index]
//     * exceeds the [size] of this array.
//     */
//    fun put(index: Int, value: Int)
//
//    /**
//     * Adds the specified [element] to this array
//     * increasing its [size].
//     */
//    fun pushBack(element: E)
//
//    /**
//     * Returns the current size of this array,
//     * it increases with [pushBack] invocations.
//     */
//    val size: Int
//}
//
//class DynamicArrayImpl<E : Any> : DynamicArray<E> {
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
//
//
//    override fun get(index: Int): E {
//        while (true) {
//            val value = core.value.get(index)
//
//            if (value != null && (value is Value || value is FixedValue)) {
//                return when (value) {
//                    is FixedValue -> value.
//                        val
//
//                    is Value -> value.value
//                    else -> {
//                        continue
//                    }
//                }
//            }
//            if (value == null) {
//                throw IndexOutOfBoundsException()
//            }
//            increase()
//        }
//    }
//
//    override fun put(index: Int, element: E) {
//        while (true) {
//            val current = core.value
//            val value = current.get(index)
//            if (value != null && value is Value<E>) {
//                if (current.array[index].compareAndSet(value, Value(element))) {
//                    // println("Finished put")
//                    return
//                }
//                continue
//            }
//
//            if (value == null) {
//                throw IndexOutOfBoundsException()
//            }
//
//            increase()
//
//        }
//    }
//
//    override fun pushBack(element: E) {
//        while (true) {
//            val current = core.value
//            val size = current.size()
//            if (size < current.capacity) {
//                if (current.array[size].compareAndSet(null, Value(element))) {
//                    current.atomicSize.compareAndSet(size, size + 1)
//                    return
//                }
//                current.atomicSize.compareAndSet(size, size + 1)
//                continue
//            }
//
//            val next = current.next.value
//            if (next == null) {
//                val newNext = Core<E>(current.capacity * 2)
//                newNext.atomicSize.getAndSet(current.capacity)
//                current.next.compareAndSet(null, newNext)
//            }
//            increase()
//        }
//
//    }
//
//
//    override val size: Int get() = core.value.size()
//
//
//    private fun increase() {
//        val current = core.value
//        val next = current.next.value ?: return
//
//        while (true) {
//
//            val pos = next.copyCount.value
//            if (pos >= current.size()) {
//                core.compareAndSet(current, next)
//                return
//            }
//            //println(pos)
//            when (val value = current.get(pos)) {
//                is OutDated -> {
//                    next.copyCount.compareAndSet(pos, pos + 1)
//                }
//
//                is FixedValue -> {
//                    next.array[pos].compareAndSet(null, value.ref)
//                    current.array[pos].compareAndSet(value, OutDated())
//                    next.copyCount.compareAndSet(pos, pos + 1)
//                }
//
//                is Value -> {
//                    if (current.array[pos].compareAndSet(value, FixedValue(value))) {
//                        next.array[pos].compareAndSet(null, value)
//                        current.array[pos].compareAndSet(value, OutDated())
//                        next.copyCount.compareAndSet(pos, pos + 1)
//                    }
//                }
//
//                else -> continue
//            }
//        }
//    }
//}
//
//private class Core<E : Any>(
//    val capacity: Int,
//) {
//    val next = atomic<Core<E>?>(null)
//    val array = atomicArrayOfNulls<ArrayValue>(capacity)
//    val atomicSize = atomic(0)
//    fun size(): Int = atomicSize.value
//    val copyCount = atomic(0)
//
//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): ArrayValue? {
//        require(index < size())
//        return array[index].value
//    }
//}

