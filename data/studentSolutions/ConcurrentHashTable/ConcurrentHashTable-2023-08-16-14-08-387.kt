@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.*
import kotlin.math.*

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table(initialCapacity))

    override fun put(key: K, value: V): V? =
        table.get().put(key, value)

    override fun get(key: K): V? =
        table.get().get(key)

    override fun remove(key: K): V? =
        table.get().remove(key)

    inner class Table(private val capacity: Int) {
        private val keys = AtomicReferenceArray<K?>(capacity)
        private val values = AtomicReferenceArray<Any?>(capacity)

        private val nextTable = AtomicReference<Table>(null)

        fun put(key: K, value: V, ifEmpty: Boolean = false): V? {
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
                        return updateValue(index, value, ifEmpty) {
                            nextTable.get()!!.put(key, value, ifEmpty)
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Try to set the key first.
                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
                            // Update the value and return the previous one.
                            return updateValue(index, value, ifEmpty) {
                                nextTable.get()!!.put(key, value, ifEmpty)
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The table should be resized.
            resize()
            // Complete the operation in the next table.
            return nextTable.get()!!.put(key, value, ifEmpty)
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
                            // The value has been moved.
                            is Moved -> {
                                // Help to complete `resize()` and go to the next table.
                                resize()
                                nextTable.get()!!.get(key)
                            }
                            // The cell stores a value, return it.
                            else -> value.let { if (it is Removed) null else it } as V?
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
                        return updateValue(index, Removed) {
                            nextTable.get()!!.remove(key)
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

        private inline fun updateValue(index: Int, update: Any?, ifEmpty: Boolean = false, onResizeNeeded: () -> V?): V? {
            while (true) {
                val value = values[index]
                if (ifEmpty && value != null) return null
                when (value) {
                    Moved -> {
                        resize()
                        return onResizeNeeded()
                    }

                    is FixedValue -> {
                        resize()
                        return onResizeNeeded()
                    }

                    else -> {
                        if (values.compareAndSet(index, value, update)) {
                            return value.let { if (it is Removed) null else it } as V?
                        }
                    }
                }
            }
        }

        private fun resize() {
            // Create a new table if needed.
            if (nextTable.get() == null) {
                val newTable = Table(capacity = this.capacity * 2)
                nextTable.compareAndSet(null, newTable)
            }
            // Transfer the elements.
            val nextTable = nextTable.get()
            for (i in 0 until capacity) {
                while (true) {
                    val key = keys[i]
                    val value = values[i]
                    when {
                        // Is the cell already moved?
                        value is Moved -> break
                        // Is the cell empty?
                        key == null || value == null -> {
                            if (values.compareAndSet(i, null, Moved)) {
                                break
                            }
                        }
                        // Does the cell store a fixed value?
                        value is FixedValue -> {
                            nextTable.put(key, value.value as V, ifEmpty = true)
                            values.compareAndSet(i, value, Moved)
                        }
                        // The cell stores a value, fix it first.
                        else -> {
                            values.compareAndSet(i, value, FixedValue(value))
                        }
                    }
                }
            }
            // Update the reference to the current table.
            while (true) {
                val curTable = table.get()
                if (curTable.capacity >= nextTable.capacity) break
                if (table.compareAndSet(curTable, nextTable)) break
            }
        }

        private fun index(key: K) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private object Moved

private object Removed

private class FixedValue(val value: Any)

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
