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

        fun put(key: K, value: V, resize: Boolean = false): V? {
            val startIndex = index(key)
            var index = startIndex
            var probes = 0
            // Search for a specified key probing.
            while (true) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return updateValue(index, value, resize) {
                            table.get().put(key, value, resize)
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Try to set the key first.
                        if (keys.compareAndSet(index, null, key) || keys[index] == key) {
                            // Update the value and return the previous one.
                            return updateValue(index, value, resize) {
                                table.get().put(key, value, resize)
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
                // Check whether resize is needed. Notable, we never call `resize()`
                // during another `resize()`, postponing the table upgrade until the
                // next `get(..)`, `put(..)`, or `remove(..)` call that detects a long
                // collision chain.
                probes++
                if (!resize) {
                    if (index == startIndex || probes == MAX_PROBES) {
                        // The table should be resized.
                        resize()
                        // Complete the operation in the next table.
                        return table.get().put(key, value, resize = false)
                    }
                }
            }
        }

        fun get(key: K): V? {
            val startIndex = index(key)
            var index = startIndex
            var probes = 0
            // Search for a specified key.
            while (true) {
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
                                // For simplicity, help to complete `resize()`
                                // and go to the next table.
                                resize()
                                return table.get().get(key)
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
                // Check whether the whole table has been checked.
                if (index == startIndex) return null
                // Check whether resize is needed.
                probes++
                if (probes == MAX_PROBES) {
                    resize()
                    return table.get().remove(key)
                }
            }
        }

        fun remove(key: K): V? {
            val startIndex = index(key)
            var index = startIndex
            var probes = 0
            // Search for a specified key.
            while (true) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Once a table cell is associated with a key,
                        // it should be associated with it forever.
                        // This way, `remove()` should only set `null` to the value slot.
                        return updateValue(index, Removed) {
                            table.get().remove(key)
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
                // Check whether the whole table has been checked.
                if (index == startIndex) return null
                // Check whether resize is needed.
                probes++
                if (probes == MAX_PROBES) {
                    resize()
                    return table.get().remove(key)
                }
            }
        }

        private inline fun updateValue(index: Int, update: Any?, duringResize: Boolean = false, onResizeNeeded: () -> V?): V? {
            while (true) {
                val value = values[index]
                if (duringResize) {
                    values.compareAndSet(index, null, update)
                    return null
                }
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
                        // Is the value removed?
                        value is Removed -> {
                            if (values.compareAndSet(i, Removed, Moved)) {
                                break
                            }
                        }
                        // Does the cell store a fixed value?
                        value is FixedValue -> {
                            nextTable.put(key, value.value as V, resize = true)
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
            table.compareAndSet(this, nextTable)
        }

        private fun index(key: K) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private object Moved

private object Removed

private class FixedValue(val value: Any)

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
