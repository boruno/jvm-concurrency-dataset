@file:Suppress("UNCHECKED_CAST", "DuplicatedCode")

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
        // TODO:
    }

    class Table<K : Any, V : Any>(private val capacity: Int) {
        private val keys = AtomicReferenceArray<Any?>(capacity)
        private val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            var i = 0
            while (i < MAX_PROBES) {
                // Read the key.
                when (keys[index]) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        while (true) {
                            val oldValue = values[index]
                            if (!values.compareAndSet(index, oldValue, value)) {
                                continue
                            }
                            return oldValue
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (!keys.compareAndSet(index, null, key) || !values.compareAndSet(index, null, value)) {
                            continue
                        }

                        // No value was associated with the key.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
                ++i
            }
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                when (keys[index]) {
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
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        fun remove(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            var i = 0
            while (i < MAX_PROBES) {
                // Read the key.
                when (keys[index]) {
                    // The cell contains the required key.
                    key -> {
                        val oldValue = values[index]
                        if (!values.compareAndSet(index, oldValue, null)) {
                            continue
                        }
                        return oldValue
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
                ++i
            }
            // The key has not been found.
            return null
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()