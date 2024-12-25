@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))
    private val table2 = AtomicReference<Table<K, V>>(null)

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val tab = table2.get() ?: table.get()
            val putResult = tab.put(key, value)
            if (putResult === NEEDS_REHASH && tab === table.get()) {
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
        return table2.get()?.get(key) ?: table.get().get(key)
    }

    override fun remove(key: K): V? {
        val oldVal = table2.get()?.markRemoved(key)
        return oldVal ?: table.get().remove(key)
    }

    private fun resize() {
        val oldTab = table.get()
        val newTab = Table<K, V>(oldTab.capacity * 2)
        if (table2.compareAndSet(null, newTab)) {
            for (oldIdx in 0 until oldTab.capacity) {
                val key = oldTab.keys[oldIdx]
                val oldVal = oldTab.values[oldIdx]
                if (key != null && oldVal != null) {
                    newTab.putIfAbsent(key, oldVal)
                }
            }
        }
        table.compareAndSet(oldTab, newTab)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<K?>(capacity)
        val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys.get(index)
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        while (true) {
                            val oldVal = values.get(index)
                            if (values.compareAndSet(index, oldVal, value)) {
                                return oldVal
                            }
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        keys.compareAndSet(index, null, key)
                        if (keys.get(index) == key) {
                            while (true) {
                                val oldVal = values.get(index)
                                if (values.compareAndSet(index, oldVal, value)) {
                                    return oldVal
                                }
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }

            return NEEDS_REHASH
        }

        fun putIfAbsent(key: K, value: V): Boolean {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys.get(index)
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return values.compareAndSet(index, null, value)
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        keys.compareAndSet(index, null, key)
                        if (keys.get(index) == key) {
                            return values.compareAndSet(index, null, value)
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            return false
        }

        fun markRemoved(key: K): V? {
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
                        while (true) {
                            val oldValue = values.get(index)
                            if (values.compareAndSet(index, oldValue, null)) {
                                return oldValue
                            }
                        }
                    }
                    // Empty cell.
                    null -> {
                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
                            // either we or someone else allocated the key, or someone else
                            while (true) {
                                val oldValue = values.get(index)
                                if (values.compareAndSet(index, oldValue, null)) {
                                    return oldValue
                                }
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            return null
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys.get(index)
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return values.get(index)
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
                        while (true) {
                            val oldValue = values.get(index)
                            if (values.compareAndSet(index, oldValue, null)) {
                                return oldValue
                            }
                        }
                    }
                    // Empty cell.
                    null -> {
//                        // The key has not been found.
//                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
//                            while (true) {
//                                val oldValue = values.get(index)
//                                if (values.compareAndSet(index, oldValue, null)) {
//                                    return oldValue
//                                }
//                            }
//                        } else {
//                            // someone else occupied the cell
//                        }
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()