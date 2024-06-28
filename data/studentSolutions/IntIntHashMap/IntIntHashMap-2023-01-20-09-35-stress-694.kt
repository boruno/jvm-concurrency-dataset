import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

import kotlinx.atomicfu.AtomicIntArray


/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core: AtomicRef<Core?> = atomic(Core(INITIAL_CAPACITY))  // = Core(INITIAL_CAPACITY)

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return ifWeNeed(key, null)
    }

    private fun ifWeNeed(key: Int, value: Int?): Int {
        while (true) {
            val oldCore = core.value
            val oldValue = if (value == null) (oldCore!!.getInternal(key)) else oldCore!!.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return if (value == null) (toValue(oldValue)) else oldValue
            core.compareAndSet(oldCore, oldCore.rehash())
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
        return ifWeNeed(key, value)
    }

    /*
    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map: IntArray = IntArray(2 * capacity)
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
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- return value
            return map[index + 1]
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index] != key) { // optimize for successful lookup
                if (map[index] == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    map[index] = key
                    break
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }
            // found key -- update value
            val oldValue = map[index + 1]
            map[index + 1] = value
            return oldValue
        }

        fun rehash(): Core {
            val newCore = Core(map.size) // map.length is twice the current capacity
            var index = 0
            while (index < map.size) {
                if (isValue(map[index + 1])) {
                    val result = newCore.putInternal(map[index], map[index + 1])
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
    }
    */

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map: AtomicIntArray = AtomicIntArray(2 * capacity)
        val shift: Int
        val next: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value
                if (curKey == key) break
                if (curKey == NULL_KEY) return NULL_VALUE
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            val res = map[index + 1].value
            return if (res >= 0) (res) else NEEDS_REHASH
        }

        /*fun putInternal(key: Int, value: Int): Int {
            var keyIndex = index(key)
            var probes = 0
            while (true) {
                val mapKey = map[keyIndex].value
                val mapValue = map[keyIndex + 1].value
                when (mapKey) {
                    key -> {
                        if (mapValue < 0) {
                            return NEEDS_REHASH
                        }
                        if (map[keyIndex + 1].compareAndSet(mapValue, value)) {
                            return mapValue
                        }
                        continue
                    }

                    NULL_KEY -> {
                        if (mapValue < 0) {
                            return NEEDS_REHASH
                        }
                        if (value == DEL_VALUE) {
                            return NULL_VALUE
                        }
                        if (map[keyIndex].compareAndSet(mapKey, key)) { // 0 -> 2
                            flag.compareAndSet(true, true)
                            if (map[keyIndex + 1].compareAndSet(mapValue, value)) {
                                return mapValue
                            } else {
                                continue
                            }
                        }
                        if (map[keyIndex].value == key) {
                            continue
                        }
                    }
                }
                if (++probes >= MAX_PROBES) {
                    return NEEDS_REHASH
                }
                if (keyIndex == 0) keyIndex = map.size
                keyIndex -= 2
            }
        }*/




        /*fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value
                val res = map[index + 1].value
                if (curKey == key) {
                    return when {
                        res >= 0 && map[curKey + 1].compareAndSet(res, value) -> res
                        res < 0 -> NEEDS_REHASH
                        else -> continue
                    }
                }

                if (curKey == NULL_KEY) {
                    // not found -- claim this slot
                    when {
                        res < 0 -> NEEDS_REHASH
                        value == DEL_VALUE -> NULL_VALUE
                        map[index].value == key -> continue
                        map[index].compareAndSet(curKey, key) -> {
                            if (map[index + 1].compareAndSet(res, value))
                                return res
                            continue
                        }
                    }

                    /*if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    map[index] = key
                    break*/
                }
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.size
                index -= 2
            }

        }*/

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value
                val res = map[index + 1].value
                if (curKey == key) {
                    return when {
                        res < 0 -> NEEDS_REHASH
                        map[index + 1].compareAndSet(res, value) -> res
                        else -> continue
                    }
                }

                if (curKey == NULL_KEY) {
                    when {
                        value == DEL_VALUE -> return NULL_VALUE
                        map[index].compareAndSet(curKey, key) -> {
                            if (!map[index + 1].compareAndSet(res, value))
                                continue
                            return res
                        }
                    }
                    if (map[index].value == key) continue
                }
                if (++probes >= MAX_PROBES) {
                    return NEEDS_REHASH
                }
                if (index == 0) index = map.size
                index -= 2
            }
        }



        fun rehash(): Core {
            next.compareAndSet(null, Core(map.size * 2))
            val newCore = next.value!!
            var index = 0
            while (index < map.size) {
                val key = map[index].value
                val value = map[index + 1].value
                if (isValue(value)) {
                    if (map[index + 1].compareAndSet(value, value * -1)) {
                        newCore.copyMap(key, value)
                        map[index + 1].compareAndSet(value * -1, Int.MIN_VALUE)
                    } else {
                        continue
                    }
                }
                if (value != Int.MIN_VALUE && value < 0) {
                    newCore.copyMap(key, value * -1)
                    map[index + 1].compareAndSet(value, Int.MIN_VALUE)
                }
                if (value == NULL_VALUE || value == DEL_VALUE) {
                    if (!map[index + 1].compareAndSet(value, Int.MIN_VALUE)) {
                        continue
                    }
                }
                index += 2
            }
            return newCore
        }

        fun copyMap(key: Int, value: Int) {
            var index = index(key)
            var probes = 0
            while (true) {
                val curKey = map[index].value
                if (curKey == key) {
                    map[index + 1].compareAndSet(NULL_VALUE, value)
                    return
                }
                if (curKey == NULL_KEY) {
                    when {
                        map[index].value == key -> continue
                        map[index].compareAndSet(curKey, key) -> {
                            map[index + 1].compareAndSet(NULL_VALUE, value)
                            return
                        }
                    }
                }
                //if (++probes >= MAX_PROBES) throw RuntimeException()
                if (index == 0) index = map.size
                index -= 2
            }
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


///**
// * Int-to-Int hash map with open addressing and linear probes.
// */
//class IntIntHashMap {
//    private val core: AtomicRef<Core?> = atomic(Core(INITIAL_CAPACITY))
//
//    /**
//     * Returns value for the corresponding key or zero if this key is not present.
//     *
//     * @param key a positive key.
//     * @return value for the corresponding or zero if this key is not present.
//     * @throws IllegalArgumentException if key is not positive.
//     */
//    operator fun get(key: Int): Int {
//        /*require(key > 0) { "Key must be positive: $key" }
//        return toValue(core.getInternal(key))*/
//        require(key > 0) { "Key must be positive: $key" }
//        /*while (true) {
//            val oldCore = core.value
//            val oldValue = oldCore!!.getInternal(key)
//            if (oldValue != NEEDS_REHASH) return toValue(oldValue)
//            core.compareAndSet(oldCore, oldCore.rehash())
//        }*/
//        return ifWeNeed(key, null)
//    }
//
//    private fun ifWeNeed(key: Int, value: Int?): Int {
//        while (true) {
//            val oldCore = core.value
//            val oldValue = if (value == null) (oldCore!!.getInternal(key)) else oldCore!!.putInternal(key, value)
//            if (oldValue != NEEDS_REHASH) return if (value == null) (toValue(oldValue)) else oldValue
//            core.compareAndSet(oldCore, oldCore.rehash())
//        }
//    }
//
//    /**
//     * Changes value for the corresponding key and returns old value or zero if key was not present.
//     *
//     * @param key   a positive key.
//     * @param value a positive value.
//     * @return old value or zero if this key was not present.
//     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
//     * [Integer.MAX_VALUE] which is reserved.
//     */
//    fun put(key: Int, value: Int): Int {
//        require(key > 0) { "Key must be positive: $key" }
//        require(isValue(value)) { "Invalid value: $value" }
//        return toValue(putAndRehashWhileNeeded(key, value))
//    }
//
//    /**
//     * Removes value for the corresponding key and returns old value or zero if key was not present.
//     *
//     * @param key a positive key.
//     * @return old value or zero if this key was not present.
//     * @throws IllegalArgumentException if key is not positive.
//     */
//    fun remove(key: Int): Int {
//        require(key > 0) { "Key must be positive: $key" }
//        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
//    }
//
//    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
//        /*while (true) {
//            val oldValue = core.putInternal(key, value)
//            if (oldValue != NEEDS_REHASH) return oldValue
//            core = core.rehash()
//        }*/
//        /*while (true) {
//            val oldCore = core.value
//            val oldValue = oldCore!!.putInternal(key, value)
//            if (oldValue != NEEDS_REHASH) return oldValue
//            core.compareAndSet(oldCore, oldCore.rehash())
//        }*/
//        return ifWeNeed(key, value)
//    }
//
//    private class Core internal constructor(capacity: Int) {
//        // Pairs of <key, value> here, the actual
//        // size of the map is twice as big.
//        val map: IntArray = IntArray(2 * capacity)
//        val shift: Int
//
//        init {
//            val mask = capacity - 1
//            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
//            shift = 32 - Integer.bitCount(mask)
//        }
//
//        fun getInternal(key: Int): Int {
//            var index = index(key)
//            var probes = 0
//            while (map[index] != key) { // optimize for successful lookup
//                if (map[index] == NULL_KEY) return NULL_VALUE // not found -- no value
//                if (++probes >= MAX_PROBES) return NULL_VALUE
//                if (index == 0) index = map.size
//                index -= 2
//            }
//            // found key -- return value
//            return map[index + 1]
//        }
//
//        fun putInternal(key: Int, value: Int): Int {
//            var index = index(key)
//            var probes = 0
//            while (map[index] != key) { // optimize for successful lookup
//                if (map[index] == NULL_KEY) {
//                    // not found -- claim this slot
//                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
//                    map[index] = key
//                    break
//                }
//                if (++probes >= MAX_PROBES) return NEEDS_REHASH
//                if (index == 0) index = map.size
//                index -= 2
//            }
//            // found key -- update value
//            val oldValue = map[index + 1]
//            map[index + 1] = value
//            return oldValue
//        }
//
//        fun rehash(): Core {
//            val newCore = Core(map.size) // map.length is twice the current capacity
//            var index = 0
//            while (index < map.size) {
//                if (isValue(map[index + 1])) {
//                    val result = newCore.putInternal(map[index], map[index + 1])
//                    assert(result == 0) { "Unexpected result during rehash: $result" }
//                }
//                index += 2
//            }
//            return newCore
//        }
//
//        /**
//         * Returns an initial index in map to look for a given key.
//         */
//        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
//    }
//}
//
//private const val MAGIC = -0x61c88647 // golden ratio
//private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
//private const val MAX_PROBES = 8 // max number of probes to find an item
//private const val NULL_KEY = 0 // missing key (initial value)
//private const val NULL_VALUE = 0 // missing value (initial value)
//private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
//private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
//
//// Checks is the value is in the range of allowed values
//private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)
//
//// Converts internal value to the public results of the methods
//private fun toValue(value: Int): Int = if (isValue(value)) value else 0