import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
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
            val curCore = core.value
            val result = curCore.getInternal(key)
            if (result != null) {
                return toValue(result)
            }
            val nextCore = curCore.nextCore.value!!
//            if (nextCore.rehashed) {
//                core.compareAndSet(curCore, nextCore)
//            } else {
//                curCore.rehash()
//            }
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
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue == null || oldValue == NEEDS_REHASH) {
                curCore.rehash()
                core.compareAndSet(curCore, curCore.nextCore.value!!)
            } else {
                return oldValue
            }
        }
    }
}

private class Core constructor(capacity: Int) {
    // Pairs of <key, value> here, the actual
    // size of the map is twice as big.
    val map = AtomicIntArray(2 * capacity)
    val shift: Int
    val nextCore = atomic<Core?>(null)
    var rehashed = false

    init {
        val mask = capacity - 1
        assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
        shift = 32 - Integer.bitCount(mask)
    }

    fun getInternal(key: Int): Int? {
        var index = index(key)
        var probes = 0
        var mapAtIndex = map[index].value
        while (mapAtIndex != key) { // optimize for successful lookup
            if (mapAtIndex == NULL_KEY) return NULL_VALUE // not found -- no value
            if (++probes >= MAX_PROBES) return NULL_VALUE
            if (index == 0) index = map.size
            index -= 2
            mapAtIndex = map[index].value
        }

        val value = map[index + 1].value
        if (isFixed(value)) {
            return toUnfixed(value)
        }
        return if (isMoved(value)) null else value
    }

    fun putInternal(key: Int, value: Int): Int? {
        var index = index(key)
        var probes = 0
        var mapAtIndex = map[index].value
        while (mapAtIndex != key) { // optimize for successful lookup
            if (mapAtIndex == NULL_KEY) {
                // not found -- claim this slot
                if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                if (map[index].compareAndSet(NULL_KEY, key)) {
                    break
                }
            }
            if (++probes >= MAX_PROBES) return NEEDS_REHASH
            if (index == 0) index = map.size
            index -= 2
            mapAtIndex = map[index].value
        }

        while (true) {
            val oldValue = map[index + 1].value
            if (oldValue < 0) {
                return null
            } else {
                if (map[index + 1].compareAndSet(oldValue, value)) {
                    return oldValue
                }
            }
        }
    }

    fun tryPutKey(key: Int): Int {
        var index = index(key)
        var probes = 0
        // first put key
        var mapAtIndex = map[index].value
        while (mapAtIndex != key) { // optimize for successful lookup
            if (mapAtIndex == NULL_KEY) {
                return if (map[index].compareAndSet(NULL_KEY, key)) index else -1
            }
            if (++probes >= MAX_PROBES) throw Exception("Impossible")
            if (index == 0) index = map.size
            index -= 2
            mapAtIndex = map[index].value
        }
        return index
    }

//    fun putIfNull(key: Int, value: Int) {
//        var index = index(key)
//        var probes = 0
//        // first put key
//        var mapAtIndex = map[index].value
//        while (mapAtIndex != key) { // optimize for successful lookup
//            if (mapAtIndex == NULL_KEY) {
//                if (map[index].compareAndSet(NULL_KEY, key)) {//<<2
//                    break
//                }
//            }
//            if (++probes >= MAX_PROBES) throw Exception("Impossible")
//            if (index == 0) index = map.size
//            index -= 2
//            mapAtIndex = map[index].value
//        }
//        // put value if value is not null
//        map[index + 1].compareAndSet(NULL_VALUE, value)//<<1
//    }

    fun rehash() {
        nextCore.compareAndSet(null, Core(map.size))

        var index = 0
        while (index < map.size) {
//            println("1 $index")
            while (true) { //<<<<<
//                println(2)
                val key = map[index].value
                println(key)
                if (key == NULL_KEY) {
                    if (map[index + 1].compareAndSet(NULL_VALUE, MOVED_VALUE)) break else continue
                }
                val value = map[index + 1].value
                println(value)
                if (isValue(value)) {
                    map[index + 1].compareAndSet(value, toFixed(value))
                    continue
                }
                if (isNullValue(value)) {
//                    val keyIndex = nextCore.value!!.tryPutKey(key) //?? is useless?????
//                    if (keyIndex > 0) {
                        if (map[index + 1].compareAndSet(value, MOVED_VALUE)) break
//                    }
                }
                if (isDeleted(value)) {
                    if (map[index + 1].compareAndSet(value, MOVED_VALUE)) break
                }
                if (isMoved(value)) break
                if (isFixed(value)) {
//                    println("aaaaaaaaaaaaa")
                    // first try put key
                    val keyIndex = nextCore.value!!.tryPutKey(key)
//                    println("bbbbbbbbbbbbbbb $keyIndex")
                    if (keyIndex >= 0) {
//                        println("ccccccccccccccc")
                        nextCore.value!!.map[keyIndex + 1].compareAndSet(NULL_VALUE, toUnfixed(value))
                        map[index + 1].compareAndSet(value, MOVED_VALUE)
                        break
                    }
                }
            }
            index += 2
        }
        rehashed = true
    }

    /**
     * Returns an initial index in map to look for a given key.
     */
    fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
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
private fun isFixed(value: Int): Boolean = value < 0 && !isMoved(value)
private fun isMoved(value: Int): Boolean = value == MOVED_VALUE
private fun isNullValue(value: Int): Boolean = value == NULL_VALUE
private fun isDeleted(value: Int): Boolean = value == DEL_VALUE

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

private fun toFixed(value: Int): Int = if (isFixed(value)) value else -1 * value
private fun toUnfixed(value: Int): Int = if (isFixed(value)) -1 * value else value