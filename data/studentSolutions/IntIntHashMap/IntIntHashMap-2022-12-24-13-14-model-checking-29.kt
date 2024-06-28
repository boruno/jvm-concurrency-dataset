import kotlinx.atomicfu.*

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
        var index = 0
        while (true) {
            index++
            if (index > 5)
                throw Exception("Too many PUT attempts")
            val oldcore = core.value
            val oldValue = oldcore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
            if(oldcore.helpMoveData()) {
                core.compareAndSet(oldcore, oldcore.nextCore.value!!)
                continue
            }
            else
            {
                core.compareAndSet(oldcore, oldcore.rehash())
            }
        }
    }

    private class Core internal constructor(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        val map = AtomicIntArray(2*capacity)
        val shift: Int
        val nextCore: AtomicRef<Core?> = atomic(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key && map[index].value != -key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            val value = map[index + 1].value
            if (value == MOVED_VALUE)
                return nextCore.value!!.getInternal(key) // Getting value from next cores
            else
                return value
        }

        fun putInternal(key: Int, value: Int): Int {
            var indexRepeat = 0
            if (value == MOVED_VALUE)
                return 0
            while(true) {
                indexRepeat++
                if (indexRepeat > 5)
                    throw Exception("Stuck in InternalPUT attempts")
                var index = index(key)
                var probes = 0
                var keyValue = map[index].value
                var tryAgain = false
                while (keyValue != key && keyValue != -key) { // optimize for successful lookup
                    if (keyValue == NULL_KEY) {
                        // not found -- claim this slot
                        if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                        if(!map[index].compareAndSet(keyValue, key)) {
                            tryAgain = true
                            break
                        }
                        break
                    }
                    if (++probes >= MAX_PROBES) return NEEDS_REHASH
                    if (index == 0) index = map.size
                    if (keyValue != key && keyValue != -key) {
                        index -= 2
                        keyValue = map[index].value
                    }
                    else
                    {
                        break
                    }
                }
                if (keyValue < 0) // Key is readonly, so we need to help
                    return NEEDS_REHASH
                if (tryAgain)
                    continue
                // found key -- update value
                val oldValue = map[index + 1].value
                if (oldValue == MOVED_VALUE) // If we have our key moved to new table, we have no power here
                    return NEEDS_REHASH
                if(map[index + 1].compareAndSet(oldValue, value)) // In case of unsuccessfull CAS, try again from beginning
                    return oldValue
                else
                    continue
            }
        }

        fun putInternalWithCAS(key: Int, value: Int, expectedValue: Int): Boolean {
            if (value == MOVED_VALUE)
                return true
            var index = index(key)
            var probes = 0
            var keyValue = map[index].value
            while (keyValue != key && keyValue != -key) { // optimize for successful lookup
                if (keyValue == NULL_KEY) {
                    // not found -- claim this slot
                    if(!map[index].compareAndSet(keyValue, key))
                        return false
                    break
                }
                if (++probes >= MAX_PROBES) return false
                if (index == 0) index = map.size
                if (keyValue != key && keyValue != -key) {
                    index -= 2
                    keyValue = map[index].value
                }
                else
                {
                    break
                }
            }
            if (keyValue < 0) // Key is readonly, so we need to help
                return false
            // found key -- update value
            val oldValue = map[index + 1].value
            if (oldValue == MOVED_VALUE) // If we have our key moved to new table, we have no power here
                return false
            return map[index + 1].compareAndSet(expectedValue, value)
        }

        // If we have rehash in progress, and we helped to finish, return true
        // If there is no rehashing in progress, we return false
        fun helpMoveData(): Boolean {
            if (nextCore.value == null)
                return false
            var indexRepeat = 0
            while(true) {
                indexRepeat++
                if (indexRepeat > 100)
                    throw Exception("Too many dataMove attempts")
                var index = 0
                var needToRestart = false
                var currentKey = map[index].value
                var currentValue = map[index + 1].value
                while (index < map.size) {
                    if (currentKey < 0 && currentValue != MOVED_VALUE && currentValue != DEL_VALUE) {
                        if(!nextCore.value!!.putInternalWithCAS(-currentKey, currentValue, NULL_VALUE)) {
                            needToRestart = true
                            break
                        }
                        if (!map[index + 1].compareAndSet(currentValue, MOVED_VALUE)) {
                            needToRestart = true
                            break
                        }
                        index += 2
                        if (index < map.size) {
                            currentKey = map[index].value
                            currentValue = map[index + 1].value
                        }
                        continue
                    }
                    if (currentValue > 0 && currentValue != DEL_VALUE)
                    {
                        if (!map[index].compareAndSet(currentKey, -currentKey)) {
                            print("175:")
                            print(currentValue)
                            println(map[index + 1].value)
                            needToRestart = true
                            break
                        }
                        if(!nextCore.value!!.putInternalWithCAS(currentKey, map[index + 1].value, NULL_VALUE)) {
                            needToRestart = true
                            break
                        }
                        if (!map[index + 1].compareAndSet(currentValue, MOVED_VALUE)) {
                            print("183:")
                            print(currentValue)
                            println(map[index + 1].value)
                            needToRestart = true
                            break
                        }
                        index += 2
                        if (index < map.size) {
                            currentKey = map[index].value
                            currentValue = map[index + 1].value
                        }
                        continue
                    }
                    if ((currentValue == DEL_VALUE || currentValue == NULL_VALUE) && currentValue != MOVED_VALUE)
                    {
                        map[index + 1].compareAndSet(currentValue, MOVED_VALUE)
                        if (map[index + 1].value != MOVED_VALUE)
                        {
                            print("196:")
                            print(currentValue)
                            println(map[index + 1].value)
                            needToRestart = true
                            break
                        }
                    }
                    index += 2
                    if (index < map.size) {
                        currentKey = map[index].value
                        currentValue = map[index + 1].value
                    }
                }
                if (needToRestart)
                    continue
                return true
            }
        }
        fun rehash(): Core {
            while(true)
            {
                if (nextCore.compareAndSet(null, Core(map.size)))
                {
                    if(!helpMoveData())
                        throw Exception("When we were rehashing, move failed")
                    return nextCore.value!!
                }
                else {
                    if(!helpMoveData())
                        throw Exception("When we were rehashing, move failed")
                    return nextCore.value!!
                }
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
private const val MOVED_VALUE = Int.MIN_VALUE
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0