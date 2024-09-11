import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val impl = HashMapImpl<Int, Int>()

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return impl[key] ?: 0
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
        return impl.put(key, value) ?: 0
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
        return impl.remove(key) ?: 0
    }
}

interface HashMap<K, V> {
    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     */
    operator fun get(key: K): V?

    /**
     * Changes value for the corresponding key
     *
     * @param key
     * @param value
     */
    operator fun set(key: K, value: V) {
        put(key, value)
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key
     * @param value
     * @return old value or zero if this key was not present.
     */
    fun put(key: K, value: V): V?

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key
     * @return old value or null if this key was not present.
     */
    fun remove(key: K): V?
}

private class HashMapImpl<K, V> : HashMap<K, V> {
    private val core = atomic(Core<K>(INITIAL_CAPACITY))

    override fun get(key: K): V? =
        getInternal(core.value, key)

    override fun put(key: K, value: V): V? =
        putInternal(core.value, key, value, weak = false)

    override fun remove(key: K): V? =
        putInternal(core.value, key, null, weak = false)

    private fun getInternal(currentCore: Core<K>, key: K): V? {
        @Suppress("UNCHECKED_CAST")
        when (val index = currentCore.get(key)) {
            NEEDS_REHASH -> return getInternal(rehash(currentCore), key)
            NOT_FOUND -> return null

            else -> {
                val cell = currentCore.values[index]
                while (true) {
                    return when (val cellValue = cell.value) {
                        is Moved -> getInternal(currentCore.next.value!!, key)
                        is Fixed<*> -> (cellValue as Fixed<V>).value
                        else -> cellValue as V?
                    }
                }
            }
        }
    }

    private fun putInternal(currentCore: Core<K>, key: K, value: V?, weak: Boolean): V? {
        @Suppress("UNCHECKED_CAST")
        when (val index = currentCore.put(key)) {
            NEEDS_REHASH -> return putInternal(rehash(currentCore), key, value, weak)
            else -> {
                val cell = currentCore.values[index]
                while (true) {
                    when (val cellValue = cell.value) {
                        is Moved ->
                            return putInternal(currentCore.next.value!!, key, value, weak)

                        is Fixed<*> -> {
                            val nextCore = currentCore.next.value!!
                            moveFixed(cell, nextCore, key, cellValue)
                            return putInternal(nextCore, key, value, weak)
                        }

                        else -> if (weak && cellValue != null || cell.compareAndSet(cellValue, value))
                            return cellValue as V?
                    }
                }
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
    private inline fun moveFixed(
        currentCell: AtomicRef<Any?>, nextCore: Core<K>, key: K, cellValue: Fixed<*>
    ) {
        putInternal(nextCore, key, (cellValue as Fixed<V>).value, weak = true)
        currentCell.value = Moved
    }

    private fun rehash(currentCore: Core<K>): Core<K> {
        if (currentCore.next.value == null) {
            val newCore = Core<K>(2 * currentCore.capacity)
            if (currentCore.next.compareAndSet(null, newCore)) transfer(currentCore)
        }
        return currentCore.next.value!!
    }

    private fun transfer(currentCore: Core<K>) {
        val nextCore = currentCore.next.value!!
        for (index in 0 until currentCore.capacity) {
            val cell = currentCore.values[index]

            while (true) {
                when (val cellValue = cell.value) {
                    is Moved -> break
                    is Fixed<*> -> {
                        moveFixed(cell, nextCore, currentCore.keys[index].value!!, cellValue)
                        break
                    }

                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        val element = cellValue as V?
                        if (element == null && cell.compareAndSet(null, Moved)) {
                            break
                        } else if (element != null && cell.compareAndSet(element, Fixed(element))) {
                            moveFixed(cell, nextCore, currentCore.keys[index].value!!, Fixed(element))
                            break
                        }
                    }
                }
            }
        }

        currentCore.transferred.value = true
        advanceCore(currentCore)
    }

    private fun advanceCore(startCore: Core<K>) {
        var currentCore = startCore
        while (currentCore.transferred.value) {
            val nextCore = currentCore.next.value!!
            core.compareAndSet(currentCore, nextCore)
            currentCore = nextCore
        }
    }

    private class Fixed<E>(val value: E)
    private object Moved

    private class Core<K>(
        val capacity: Int
    ) {
        val shift: Int

        val transferred = atomic(false)
        val keys = atomicArrayOfNulls<K>(capacity)
        val values = atomicArrayOfNulls<Any>(capacity)
        val next = atomic<Core<K>?>(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun get(key: K): Int {
            var index = index(key)

            var probes = 0
            while (true) {
                val currentKey = keys[index].value ?: return NOT_FOUND
                if (currentKey == key) return index
                if (++probes == MAX_PROBES) return NEEDS_REHASH
                if (++index == capacity) index = 0
            }
        }

        fun put(key: K): Int {
            var index = index(key)

            var probes = 0
            while (true) {
                val currentKey = keys[index].value
                    ?: if (keys[index].compareAndSet(null, key))
                        return index
                    else
                        continue
                if (currentKey == key) return index
                if (++probes == MAX_PROBES) return NEEDS_REHASH
                if (++index == capacity) index = 0
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: K): Int = (key.hashCode() * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1
private const val NOT_FOUND = -2

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)