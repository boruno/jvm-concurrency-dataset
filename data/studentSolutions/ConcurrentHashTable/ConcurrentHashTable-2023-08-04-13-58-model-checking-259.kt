@file:Suppress("UNCHECKED_CAST")

package day4

import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table<K, V>(initialCapacity))

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
        while (true) {
            val oldTable = table.value
            val newTab = Table<K, V>(oldTable.capacity * 2)
            for (i in 0 until oldTable.keys.size) {
                val oldK = oldTable.keys[i].value
                val oldV = oldTable.values[i].value
                if (oldK != null && oldV != null) {
                    assert(newTab.put(oldK, oldV) == null)
                }
            }
            if (table.compareAndSet(oldTable, newTab)) {
                break
            }
        }
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                // The cell does not store a key.
                if (keys[index].compareAndSet(null, key) || keys[index].value==key) {
                    // Insert the key/value pair into this cell.
//                    keys[index].value = key
                    return values[index].getAndSet(value)
                    // No value was associated with the key.
//                    return null
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return values[index].value
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }
        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

        fun remove(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {

                        // Mark the slot available for `put(..)`,
                        // but do not stop on this cell when searching for a key.
                        // For that, replace the key with `REMOVED_KEY`.
//                        keys[index].value = REMOVED_KEY
                        // Read the value associated with the key and replace it with `null`.
                        return values[index].getAndSet(null)
//                        val oldValue = values[index].value
//                        values[index].value = null
//                        return oldValue
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()