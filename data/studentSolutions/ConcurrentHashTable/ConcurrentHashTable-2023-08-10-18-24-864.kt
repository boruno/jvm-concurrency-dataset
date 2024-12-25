@file:Suppress("UNCHECKED_CAST")

//package day4

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
        if (!curTable.nextTable.compareAndSet(null, newTable)) return

        // Copy all elements from the current table to the new one.
        while (!curTable.isMoved()) {
            repeat(curTable.capacity) { index ->
                curTable.move(index)
            }
        }
        // Replace the current table with the new one.
        table.set(newTable)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)

        val nextTable = AtomicReference<Table<K, V>>(null)

        object Moved
        object Removed
        data class FixedValue(val value: Any?)

        fun isMoved(): Boolean = (0 until capacity).all { index -> isMoved(index) }

        fun isMoved(index: Int): Boolean {
            // We don't need to move empty cells
            return values[index] is Moved
        }
        fun move(index: Int): Boolean {
            while (true) {
                when (val prevValue = values[index]) {
                    Removed, null -> {
                        // We don't need to move the element.
                        // It was removed, so we ignore this cell but mark it as MovedCell.
                        if (values.compareAndSet(index, prevValue, Moved)) {
                            return true
                        }
                    }
                    Moved -> {
                        // The element was already moved.
                        return false
                    }
                    is FixedValue -> {
                        // Claim new slot.
                        val nextTable = nextTable.get()
                        val newIndex = nextTable.claimNewSlot(keys[index] as K)
                        // Copy the value (only if it wasn't already changed).
                        if (nextTable.values.compareAndSet(newIndex, null, prevValue.value)) {
                            // Mark as moved.
                            if (values.compareAndSet(index, prevValue, Moved)) {
                                return true
                            }
                        }

                    }
                    else -> {
                        // We need to move the element to the new table.
                        // Fix the value; then go to 'MovedValue' branch.
                        val movedValue = FixedValue(prevValue)
                        values.compareAndSet(index, prevValue, movedValue)
                    }
                }
            }
        }

        fun claimNewSlot(key: K): Int {
            var index = index(key)
            while (true) {
                val curKey = keys[index]
                when (curKey) {
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (keys.compareAndSet(index, null, key)) {
                            return index
                        }
                    }
                    key -> {
                        return index
                    }
                    else -> {
                        // Process the next cell, use linear probing.
                        index = (index + 1) % capacity
                    }
                }
            }
        }

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.

            var probes = 0
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            while(true) {
                // Read the key.
                val curKey = keys[index]
                val nextTable = nextTable.get()
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // The cell is in the process of moving.
                        if (values[index] is FixedValue) {
                            // Continue to move the value.
                            move(index)
                        }
                        // The cell was already moved to the next table.
                        if (values[index] is Moved) {
                            // Add the value to the new table.
                            return nextTable.put(key, value)
                        }
                        // Update the value and return the previous one.
                        val oldValue = values[index]
                        if (values.compareAndSet(index, oldValue, value)) {
                            if (oldValue == Removed) return null
                            return oldValue
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        if (nextTable != null) {
                            nextTable.put(key, value)
                        }
                        else {
                            // Insert the key/value pair into this cell.
                            if (keys.compareAndSet(index, null, key)) {
                                if (values.compareAndSet(index, null, value)) {
                                    return null
                                }
                            }
                        }
                    }
                    // Other value
                    else -> {
                        // Inform the caller that the table should be resized.
                        if (++probes == MAX_PROBES) return NEEDS_REHASH
                        // Process the next cell, use linear probing.
                        index = (index + 1) % capacity
                    }
                }
            }
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            var probes = 0
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            while (true) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        val value = values[index]
                        if (value is FixedValue) return value.value as V?
                        if (value is Moved) return nextTable.get().get(key)
                        if (value is Removed) return null
                        return value as V?
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    // Other value
                    else -> {
                        if (++probes == MAX_PROBES) return null
                        // Process the next cell, use linear probing.
                        index = (index + 1) % capacity
                    }
                }
            }

        }

        fun remove(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.

            var probes = 0
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            while(true) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Mark the slot available for `put(..)`,
                        // but do not stop on this cell when searching for a key.
                        // Read the value associated with the key and replace it with `null`.
                        val oldValue = values[index]
                        if (oldValue is FixedValue) {
                            move(index)
                            continue
                        }
                        if (oldValue is Moved) return nextTable.get().remove(key)
                        if (oldValue is Removed) return null
                        if (values.compareAndSet(index, oldValue, Removed)) return oldValue as V?
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    // Other value
                    else -> {
                        if (++probes == MAX_PROBES) return null
                        // Process the next cell, use linear probing.
                        index = (index + 1) % capacity
                    }
                }
            }
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()