@file:Suppress("UNCHECKED_CAST", "DuplicatedCode")

package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            tryReplacingOldTable()
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
        tryReplacingOldTable()
        return table.get().get(key)
    }

    override fun remove(key: K): V? {
        tryReplacingOldTable()
        return table.get().remove(key)
    }

    private fun tryReplacingOldTable() {
        val curTable = table.get()
        if (curTable.isAllMoved()) {
            table.compareAndSet(curTable, curTable.next.get())
        }
    }

    private fun resize() {
        val curTable = table.get()
        curTable.next.compareAndSet(null, Table(curTable.capacity))
    }

    class Table<K : Any, V : Any>(val capacity: Int) {

        object Moved
        inner class Wrapper(val value: Any)

        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        val next: AtomicReference<Table<K, V>> = AtomicReference(null)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            val nextTable = next.get()
            if (nextTable != null) {
                copyValues(nextTable)
                return nextTable.put(key, value)
            }
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
                        return toValue(key, values.getAndSet(index, value))
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
                            // No value was associated with the key.
                            return toValue(key, values.getAndSet(index, value))
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
                        return toValue(key, values[index])
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
            val nextTable = next.get()
            if (nextTable != null) {
                copyValues(nextTable)
                return nextTable.remove(key)
            }
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        return toValue(key, values.getAndSet(index, null))
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

        private fun copyValues(nextTable: Table<K, V>) {
            for (i in 0 until capacity) {
                while (true) {
                    when (val v = values[i]) {
                        is Table<*, *>.Wrapper -> {
                            nextTable.put(keys[i] as K, v.value as V)
                            if (values.compareAndSet(i, v, Moved)) {
                                break
                            }
                        }

                        null -> {
                            if (values.compareAndSet(i, null, Moved)) {
                                break
                            }
                        }

                        Moved -> break

                        else -> values.compareAndSet(i, v, Wrapper(v))
                    }
                }
            }
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

        fun isAllMoved(): Boolean {
            for (i in 0 until capacity) {
                if (values[i] !== Moved) {
                    return false
                }
            }
            return true
        }

        private fun toValue(key: K, value: Any?): V? = when (value) {
            Moved -> next.get().get(key)
            is Table<*, *>.Wrapper -> value.value as V?
            else -> value as V?
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()