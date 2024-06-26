@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val curTable = table.get()
            val putResult = curTable.put(key, value, onlyNotInitialized = false)
            if (putResult === NEEDS_REHASH) {
                // The current table is too small to insert a new key.
                // Create a new table of x2 capacity,
                // copy all elements to it,
                // and restart the current operation.
                resize(curTable)
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

    private fun resize(curTable: Table) {
        val myNewTable = Table(capacity = curTable.capacity * 2)
        curTable.next.compareAndSet(null, myNewTable)
        val newTable = curTable.next.get()!!
        for (i in 0 until curTable.capacity) {
            moveValue(curTable, newTable, i)
        }
        table.compareAndSet(curTable, newTable)
    }

    private fun moveValue(
        curTable: Table,
        newTable: Table,
        i: Int,
    ) {
        while (true) {
            val value = curTable.values[i]
            if (value is Fixed<*> || value === Moved) break
            val newValue = if (value == null || value === Removed) Moved else Fixed(value)
            if (curTable.values.compareAndSet(i, value, newValue)) break
        }
        moveValueIfFixed(curTable, newTable, i)
    }

    private fun moveValueIfFixed(
        curTable: Table,
        newTable: Table,
        i: Int,
    ) {
        val value = curTable.values[i]
        if (value is Fixed<*>) {
            val x = newTable.put(curTable.keys[i] as K, value.value as V, onlyNotInitialized = true)
            if (x === NEEDS_REHASH) error("meow")
            curTable.values[i] = Moved
        }
    }

    inner class Table(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        val next = AtomicReference<Table?>(null)

        tailrec fun put(key: K, value: V, onlyNotInitialized: Boolean): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                val oldValue = values[index]
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return when {
                            onlyNotInitialized && oldValue != null -> oldValue
                            oldValue === Moved || oldValue is Fixed<*> -> {
                                val nextTable = next.get()!!
                                moveValueIfFixed(this, nextTable, index)
                                nextTable.put(key, value, onlyNotInitialized)
                            }

                            values.compareAndSet(index, oldValue, value) -> oldValue.convertToUserValue()

                            else -> put(key, value, onlyNotInitialized)
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        return when {
                            oldValue === Moved || oldValue is Fixed<*> -> {
                                val nextTable = next.get()!!
                                moveValueIfFixed(this, nextTable, index)
                                nextTable.put(key, value, onlyNotInitialized)
                            }

                            !keys.compareAndSet(index, null, key) -> put(key, value, onlyNotInitialized)
                            !values.compareAndSet(index, oldValue, value) -> put(key, value, onlyNotInitialized)

                            // No value was associated with the key.
                            else -> null
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
            // TODO: and add the logic related to moving key/value pairs to a new table.
            TODO("Implement me!")
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                val curValue = values[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        if (curValue === Moved || curValue is Fixed<*>) {
                            val nextTable = next.get()!!
                            moveValueIfFixed(this, nextTable, index)
                            return nextTable.get(key)
                        }
                        // Read the value associated with the key.
                        return curValue.convertToUserValue()
                    }
                    // Empty cell.
                    null -> {
                        if (curValue === Moved || curValue is Fixed<*>) {
                            val nextTable = next.get()!!
                            moveValueIfFixed(this, nextTable, index)
                            return nextTable.get(key)
                        }
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

        tailrec fun remove(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                val oldValue = values[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Mark the slot available for `put(..)`,
                        // but do not stop on this cell when searching for a key.
                        // Read the value associated with the key and replace it with `null`.

                        if (oldValue === Moved || oldValue is Fixed<*>) {
                            val nextTable = next.get()!!
                            moveValueIfFixed(this, nextTable, index)
                            return nextTable.remove(key)
                        }
                        if (!values.compareAndSet(index, oldValue, Removed)) return remove(key)
                        return oldValue.convertToUserValue()
                    }
                    // Empty cell.
                    null -> {
                        if (oldValue === Moved || oldValue is Fixed<*>) {
                            val nextTable = next.get()!!
                            moveValueIfFixed(this, nextTable, index)
                            return nextTable.remove(key)
                        }
                        // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
            // TODO: and add the logic related to moving key/value pairs to a new table.
            TODO("Implement me!")
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

        fun Any?.convertToUserValue(): V? = when {
            this == null -> null
            this === Moved -> error("Should be already handled")
            this === Removed -> null
            this is Fixed<*> -> error("Should be already handled")
            else -> this
        } as V?
    }
}

private class Fixed<V>(val value: V)

private val Moved = Any()
private val Removed = Any()

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()