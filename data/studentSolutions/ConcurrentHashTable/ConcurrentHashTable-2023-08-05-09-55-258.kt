@file:Suppress("UNCHECKED_CAST")

package day4

import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table<K, V>(initialCapacity))
    private val nextTable = atomic<Table<K, V>?>(null)
    companion object {
        private const val SLOT_NOT_FOUND = -1
        private const val SLOT_NEED_REHASH = -2
    }

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = table.value.put(key, value)
            if (putResult === NEEDS_REHASH) {
                // The current table is too small to insert a new key.
                // Create a new table of x2 capacity,
                // copy all elements to it,
                // and restart the current operation.
                resize()
            } else {
                // The operation has been successfully performed,
                // return the previous value associated with the key.
                return putResult as V?
            }
        }
    }

    override fun get(key: K): V? {
        return table.value.get(key)
    }

    override fun remove(key: K): V? {
        return table.value.remove(key)
    }

    private fun resize() {
        // TODO:
        val nextCapacity = table.value.capacity * 2
        var newTable = nextTable.value ?: Table(nextCapacity)
        nextTable.compareAndSet(null, newTable)
        newTable = this.nextTable.value ?: return
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)

        fun getSlotForKey(key: K, createNew: Boolean): Int {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return index
                    }
                    // The cell does not store a key.
                    null -> {
                        if (createNew) {
                            // Insert the key/value pair into this cell.
                            keys[index].compareAndSet(null, key)
                            if (keys[index].value == key) {
                                return index
                            }

                        } else {
                            return SLOT_NOT_FOUND
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return SLOT_NEED_REHASH
        }

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            return when (val slot = getSlotForKey(key, true)) {
                SLOT_NEED_REHASH -> NEEDS_REHASH
                else -> values[slot].getAndSet(value)
            }
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            return when (val slot = getSlotForKey(key, false)) {
                SLOT_NOT_FOUND -> null
                else -> values[slot].value
            }
        }

        fun remove(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            return when (val slot = getSlotForKey(key, false)) {
                SLOT_NOT_FOUND -> null
                else -> values[slot].getAndSet(null)
            }
        }

        internal fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()