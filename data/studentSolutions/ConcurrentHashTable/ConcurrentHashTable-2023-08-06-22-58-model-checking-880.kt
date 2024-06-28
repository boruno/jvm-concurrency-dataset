@file:Suppress("UNCHECKED_CAST")

package day4

import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table<K, V>(initialCapacity))

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
        // Get a reference to the current table.
        val currentTable = table.value
        // Create a new table of x2 capacity.
        var newTable = Table<K, V>(currentTable.capacity * 2)
        // Try to add a reference to the next table. It will mean that the resizing process is started
        if (!currentTable.next.compareAndSet(null, newTable)) {
            // Another thread already started table resizing.
            // So `currentTable.next.value` is not null here and we may help to process it
            newTable = currentTable.next.value!!
        }
        // Copy all elements from the current table to the new one.
        repeat(currentTable.capacity) { index ->
            while (true) {
                val key = currentTable.keys[index].value
                if (key == null) {
                    // If key is empty, try to mark this cell as moved to prevent future `put` in this cell
                    if (currentTable.values[index].compareAndSet(null, Moved)) {
                        break
                    }
                    else {
                        continue
                    }
                }

                when (val value = currentTable.values[index].value) {
                    null -> {
                        // Marked cell as moved and not put anything to new table
                        // since `(key, null)` pair means the table doesn't contain anything by `key`
                        if (currentTable.values[index].compareAndSet(null, Moved)) {
                            break
                        }
                    }
                    Moved -> break
                    is Moving<*> -> {
                        currentTable.copyValueToNextTable(key, index)
                    }
                    else -> {
                        val movingValue = Moving(value)
                        if (currentTable.values[index].compareAndSet(value, movingValue)) {
                            newTable.put(key, value as V)
                            currentTable.values[index].compareAndSet(movingValue, Moved)
                            // If CAS above returned true, we successfully moved the value.
                            // Otherwise, another thread did it
                            // since the only way not to have `Moving` in the cell is `Moved` state
                            break
                        }
                    }
                }
            }
        }
        // Replace the current table with the new one.
        table.compareAndSet(currentTable, newTable)
    }

    private class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<Any?>(capacity)

        val next: AtomicRef<Table<K, V>?> = atomic(null)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace all writes to update key/value with CAS-s.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                while (true) {
                    // Read the key.
                    val curKey = keys[index].value
                    when (curKey) {
                        // The cell contains the specified key.
                        key -> {
                            // Update the value and return the previous one.
                            return when (val oldValue = values[index].value) {
                                is Moved -> next.value?.put(key, value)
                                is Moving<*> -> {
                                    copyValueToNextTable(key, index)
                                    next.value?.put(key, value)
                                }
                                else -> {
                                    if (values[index].compareAndSet(oldValue, value)) {
                                        oldValue
                                    } else {
                                        continue
                                    }
                                }
                            }
                        }
                        // The cell does not store a key.
                        null -> {
                            // Insert the key/value pair into this cell.
                            if (!keys[index].compareAndSet(null, key)) {
                                continue
                            }

                            return when (val oldValue = values[index].value) {
                                is Moved -> next.value?.put(key, value)
                                is Moving<*> -> {
                                    copyValueToNextTable(key, index)
                                    next.value?.put(key, value)
                                }
                                else -> {
                                    if (values[index].compareAndSet(oldValue, value)) {
                                        oldValue
                                    } else {
                                        continue
                                    }
                                }
                            }
                        }
                        else -> break
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
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return when (val currentValue = values[index].value) {
                            // Value was already move to new table during rehashing
                            Moved -> next.value?.get(key)
                            is Moving<*> -> {
                                copyValueToNextTable(key, index)
                                next.value?.get(key)
                            }
                            else -> currentValue as V?
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
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace the write to update the value with CAS.
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
                        while (true) {
                            // Read the value associated with the key and replace it with `null`.
                            return when (val oldValue = values[index].value) {
                                Moved -> next.value!!.remove(key)
                                is Moving<*> -> {
                                    copyValueToNextTable(key, index)
                                    next.value!!.remove(key)
                                }

                                else -> {
                                    if (values[index].compareAndSet(oldValue, null)) {
                                        oldValue as V?
                                    } else {
                                        continue
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

        fun copyValueToNextTable(key: K, index: Int) {
            when (val currentValue = values[index].value) {
                Moved -> return
                is Moving<*> -> {
                    next.value!!.put(key, currentValue.value as V)
                    // CAS?
                    values[index].value = Moved
                }
                else -> error("Unreachable")
            }
        }

    }

    private class Moving<V>(val value: V)
    private object Moved
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = object : Any() {
    override fun toString(): String = "NEEDS_REHASH"
}