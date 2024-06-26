@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val curTable = table.get()
            val putResult = curTable.put(key, value)
            if (putResult === NEEDS_REHASH) {
                // The current table is too small to insert a new key.
                // Create a new table of x2 capacity,
                // copy all elements to it,
                // and restart the current operation.
                val newTable = curTable.resize()
                table.compareAndSet(curTable, newTable)
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

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        private val newTable = AtomicReference<Table<K, V>>(null)

        fun put(key: K, value: V): Any? {
            return put(key, value, null)
        }

        private fun put(key: K, value: V, v: Any?): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            while (true) {
                repeat(MAX_PROBES) {
                    // Read the key.
                    val curKey = keys[index]
                    when (curKey) {
                        // The cell contains the specified key.
                        key -> {
                            // Update the value and return the previous one.
                            return setValue(index, key, value)
                        }
                        // The cell does not store a key.
                        null -> {
                            // Insert the key/value pair into this cell.
                            if (
                                keys.compareAndSet(index, null, key)
                                || keys.get(index) == key
                            ) {
                                return setValue(index, key, value)
                            }
                        }
                    }
                    // Process the next cell, use linear probing.
                    index = (index + 1) % capacity
                }
                // Inform the caller that the table should be resized.
                return NEEDS_REHASH
            }
        }

        private fun setValue(index: Int, key: K, value: V): Any? {
            while (true) {
                when (val oldValue = values.get(index)) {
                    is Fixed -> {
                        val put = newTable.get().put(key, value, oldValue.v)
                        values.set(index, TOMBSTONE)
                        return put
                    }

                    TOMBSTONE -> {
                        return newTable.get().put(key, value, null)
                    }

                    else -> {
                        if (values.compareAndSet(index, oldValue, value)) {
                            return oldValue
                        }
                    }
                }
            }
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
                        val v = values[index]
                        return when (v) {
                            is Fixed -> {
                                newTable.get().get(key) ?: v.v
                            }
                            TOMBSTONE -> {
                                newTable.get().get(key)
                            }
                            else -> {
                                v
                            }
                        } as V?
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
            return remove(key, null)
        }

        fun remove(key: K, v: V?): V? {
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
                            when (val oldValue = values.get(index)) {
                                is Fixed -> {
                                    val put = newTable.get().remove(key, oldValue.v as V?)
                                    values.set(index, TOMBSTONE)
                                    return put
                                }
                                TOMBSTONE -> {
                                    return newTable.get().remove(key, null)
                                }
                                else -> {
                                    if (values.compareAndSet(index, oldValue, null)) {
                                        return oldValue as V?
                                    }
                                }
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

        fun resize(): Table<K, V>? {
            // Create a new table of x2 capacity.
            newTable.compareAndSet(null, Table(capacity * 2))
            val newTable = newTable.get()

            // Copy all elements from the current table to the new one.
            repeat(capacity) { index ->
                while (true) {
                    val key = keys[index]
                    val value = values[index]
                    if (value !is Fixed) {
                        if (values.compareAndSet(index, value, Fixed(value))) {
                            // Is the cell non-empty and does a value present?
                            if (key != null && value != null) {
                                newTable.put(key as K, value as V)
                            }
                            break
                        }
                    } else {
                        break
                    }
                }
            }
            return newTable
        }
    }

    class Fixed(val v: Any?);
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()
private val TOMBSTONE = Any()
