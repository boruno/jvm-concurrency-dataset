@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.*
import kotlin.math.*

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = table.get().put(key, value)
            if (putResult === NEEDS_RESIZE) {
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
        return table.get().get(key)
    }

    override fun remove(key: K): V? {
        return table.get().remove(key)
    }

    private fun resize() {
        // Update the current table reference.
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)

        val nextTable = AtomicReference<Table<K, V>>(null)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return updateValue(key, index, null)
                    }
                    // The cell does not store a key.
                    null -> {
                        // Try to set the key first.
                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
                            // Update the value and return the previous one.
                            return updateValue(key, index, null)
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return NEEDS_RESIZE
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        val value = values[index]
                        return when (value) {
                            // The value is fixed.
                            is FixedValue -> value.value as V?
                            // The value has been moved, go to the next table.
                            is Moved -> nextTable.get()!!.get(key)
                            // The cell stores a value, return it.
                            else -> value as V?
                        }
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

        fun remove(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Once a table cell is associated with a key,
                        // it should be associated with it forever.
                        // This way, `remove()` should only set `null` to the value slot.
                        return updateValue(key, index, null)
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

        private fun updateValue(key: K, index: Int, update: V?): V? {
            while (true) {
                val value = values[index]
                when (value) {
                    Moved -> {
                        resize()
                        // restart
                    }
                    is FixedValue -> {
                       resize()
                        // restart
                    }
                }
            }
        }

        private fun resize() {

        }

        private fun index(key: K) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private object Moved

private class FixedValue(val value: Any)

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_RESIZE = Any()
