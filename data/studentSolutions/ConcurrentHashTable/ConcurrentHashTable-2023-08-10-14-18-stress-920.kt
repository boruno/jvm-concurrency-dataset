@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = table.get().put(key, value)
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
        return table.get().get(key)
    }

    override fun remove(key: K): V? {
        return table.get().remove(key)
    }

    private fun resize() {
        // Get a reference to the current table.
        val curTable = table.get()
        // Create a new table of x2 capacity.
        val newTable = Table<K, V>(curTable.capacity * 2)
        // Copy all elements from the current table to the new one.
        repeat(curTable.capacity) { index ->
            val key = curTable.keys[index]
            val value = curTable.values[index]
            // Is the cell non-empty and does a value present?
            if (key != null && value != null) {
                newTable.put(key as K, value)
            }
        }
        // Replace the current table with the new one.
        table.compareAndSet(curTable, newTable)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            iterateCells(key) { index ->
                // Read the key.
                while (true) {
                    val curKey = keys[index]
                    when (curKey) {
                        // The cell contains the specified key.
                        key -> {
                            // Update the value and return the previous one.
                            return values.getAndSet(index, value)
                        }
                        // The cell does not store a key.
                        null -> {
                            // Insert the key/value pair into this cell.
                            if (keys.compareAndSet(index, null, key)) {
                                return values.getAndSet(index, value)
                            }
                        }
                        // The cell contains different key.
                        else -> break
                    }
                }
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            iterateCells(key) { index ->
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return values[index]
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    // Skip different keys.
                }
            }
            // The key has not been found.
            return null
        }

        fun remove(key: K): V? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            iterateCells(key) { index ->
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key and replace it with `null`.
                        // Leave key non-null: yes, we grab this cell forever, but it simplifies concurrent algorithm.
                        return values.getAndSet(index, null)
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    // Skip different keys.
                }
                }
            // The key has not been found.
            return null
        }

        private inline fun iterateCells(key: K, process: (Int) -> Unit) {
            var index = index(key)
            repeat(MAX_PROBES) {
                process(index)
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()