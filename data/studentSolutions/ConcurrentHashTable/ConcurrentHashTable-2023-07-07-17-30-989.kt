@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table(initialCapacity))

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
        table.value.resize()
    }

    inner class Table(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<Any?>(capacity)
        val next: AtomicRef<Table?> = atomic(null)

        fun resize(): Table {
            val newTableAttempt = Table(capacity * 2)
            val newTable = if (next.compareAndSet(null, newTableAttempt))  {
                newTableAttempt
            }  else next.value!!
            for (i in 0 until keys.size) {
                while (true) {
                    val oldValue = values[i].value
                    if (oldValue == Moved) break
                    if (oldValue is MovedValue<*>) {
                        newTable.putForCopy(keys[i].value!!, oldValue.value as V)
                        values[i].value = Moved
                        break
                    }
                    if (oldValue != null && oldValue != Removed) {
                        if (values[i].compareAndSet(oldValue, MovedValue(oldValue))) {
                            newTable.putForCopy(keys[i].value!!, oldValue as V)
                            values[i].value = Moved
                            break
                        }
                    } else {
                        if (values[i].compareAndSet(oldValue, Moved)) {
                            break
                        }
                    }
                }
            }
            table.compareAndSet(this, newTable)
            return newTable
        }

        fun putForCopy(key: K, value: V) {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(capacity) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        values[index].compareAndSet(null, value)
                        return
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (keys[index].compareAndSet(curKey, key) || keys[index].value == key) {
                            values[index].compareAndSet(null, value)
                            return
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
        }

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
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
                        while (true) {
                            val oldValue = values[index].value
                            if (oldValue === Moved || oldValue is MovedValue<*>) {
                                return resize().put(key, value)
                            }
                            if (values[index].compareAndSet(oldValue, value)) {
                                if (oldValue === Removed) return null
                                return oldValue
                            }
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (keys[index].compareAndSet(curKey, key) || keys[index].value == key) {
                            while (true) {
                                val oldValue = values[index].value
                                if (oldValue === Moved || oldValue is MovedValue<*>) {
                                    return resize().put(key, value)
                                }
                                if (values[index].compareAndSet(oldValue, value)) {
                                    if (oldValue === Removed) return null
                                    return oldValue
                                }
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
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
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        val oldValue = values[index].value
                        if (oldValue === Moved) {
                            return resize().get(key)
                        }
                        if (oldValue is MovedValue<*>) {
                            if (oldValue.value === Removed) return null
                            return oldValue.value as V?
                        }
                        // Read the value associated with the key.
                        if (oldValue === Removed) return null
                        return oldValue as V?
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
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Mark the slot available for `put(..)`,
                        // but do not stop on this cell when searching for a key.
                        // For that, replace the key with `REMOVED_KEY`.
                        // Read the value associated with the key and replace it with `null`.
                        while (true) {
                            val oldValue = values[index].value
                            if (oldValue === Moved || oldValue is MovedValue<*>) {
                                return resize().remove(key)
                            }
                            if (values[index].compareAndSet(oldValue, Removed)) {
                                if (oldValue === Removed) return null
                                return oldValue as V?
                            }
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

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()
private val Moved = Any()
private val Removed = Any()
private class MovedValue<E>(val value: E)