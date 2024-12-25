@file:Suppress("UNCHECKED_CAST")

//package day4

import day1.MSQueue
import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    private val tableQueue = MSQueue<Table<K, V>>()

    init {
        tableQueue.enqueue(table.get())
    }

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = table.get().put(key, Value.Real(value))
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
        // Create a new table
        while (true) {
            val currentTable = table.get()
            val newTable = Table<K, V>(currentTable.capacity * 2)
            // is there a new table already?
            if (currentTable.next.compareAndSet(null, newTable)) {
                transferAllElements(currentTable!!, newTable)
                table.compareAndSet(currentTable, newTable)
                return
            }
        }
    }

    private fun transferAllElements(
        currentTable: Table<K, V>,
        newTable: Table<K, V>
    ) {
        for (i in 0 until currentTable.keys.length()) {
            val key = currentTable.keys[i]
            val value = currentTable.values[i]
            when {
                key == null -> {
                    // do nothing, mark as MOVED
                    newTable.put(null, Value.Moved)
                }
                value == null || value is Value.Moved || value is Value.Fixed -> {
                    // partially removed
                    newTable.put(key as K, Value.Moved)
                }
                value is Value.Real -> {
                    // key and value exist
                    // fix value
                    currentTable.values.set(i, Value.Fixed(value.value))
                    // claim new slot
                    newTable.put(key as K, null)
                    // copy the value
                    newTable.put(key, Value.Real(value.value))
                    // mark as moved
                    currentTable.values.set(i, Value.Moved)
                }
            }
        }
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Value<V>?>(capacity)
        val next = AtomicReference<Table<K, V>>(null)

        fun put(key: K?, value: Value<V>?): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            val nextTable = next.get()
            if (nextTable != null) {
                return nextTable.put(key, value)
            }

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
                        // Key is the same, we SHOULD wait here to update the value
                        return compareWithOldAndExchange(index, value)
                    }
                    // The cell does not store a key.
                    null -> {
                        // update the key, being careful -- key might have been inserted by now
                        if (keys.compareAndSet(index, null, key)) {
                            // hooray, our key is inserted!
                            // now the same with installing the value
                            return compareWithOldAndExchange(index, value)
                        } else if (keys[index] == key) {
                            // same key already installed, can update the value
                            return compareWithOldAndExchange(index, value)
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
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        val value = values[index]
                        if (value is Value.Moved) {
                            return next.get().get(key)
                        }
                        if (value is Value.Real) {
                            return value.value
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
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            val nextTable = next.get()
            if (nextTable != null) {
                return nextTable.remove(key)
            }

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
                        // TODO: Once a table cell is associated with a key,
                        // TODO: it should be associated with it forever.
                        // TODO: This way, `remove()` should only set `null` to the value slot,
                        // TODO: without replacing the key slot with `REMOVED_KEY`.
                        return (compareWithOldAndExchange(index, null) as? Value.Real)?.value
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

        private fun compareWithOldAndExchange(index: Int, value: Value<V>?): Value<V>? {
            while (true) {
                val oldValue = values[index]
                if (values.compareAndSet(index, oldValue, value)) {
                    return oldValue
                }
            }
        }

        private fun index(key: Any?) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }

    // set the last bit
    private fun markMoved(value: V?): V {
        val result: Any? = if (value is Int) {
            if (value % 2 == 0) {
                value + 1
            }
            else value
        }
        else value
        return result as V
    }

    private fun isMoved(value: V?): Boolean = if (value is Int) {
        value % 2 != 0
    } else false

    // set the 2nd to last bit
    private fun markFixed(value: V?): V {
        val result: Any? = if (value is Int) {
            if (value % 4 < 2) {
                value + 2
            }
            else value
        }
        else value
        return result as V
    }

    private fun isFixed(value: V): Boolean = if (value is Int) {
        value % 4 >= 2
    } else false

    private fun toExtendedValue(value: V) = if (value is Int) {
        ((value + 512) * 4) as V
    }
    else value

    private fun fromExtendedValue(extendedValue: V): V = if (extendedValue is Int) {
        (extendedValue / 4 - 512) as V
    }
    else extendedValue

}

sealed interface Value<out V : Any> {
    class Real<out V : Any>(val value: V) : Value<V>
    object Moved : Value<Nothing>
    class Fixed<out V : Any>(val underlyingValue: V) : Value<V>
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()