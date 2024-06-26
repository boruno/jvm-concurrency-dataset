@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
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
        // TODO:
        while (true) {
            val oldTable = table.get()
            val newTable = Table<K, V>(oldTable.capacity * 2)
            if (oldTable.next.compareAndSet(null, newTable)) {
                transferElements(oldTable, newTable)
                table.compareAndSet(oldTable, newTable)
                return
            } else {
                transferElements(oldTable, newTable)
                table.compareAndSet(oldTable, oldTable.next.get())
            }
        }
    }

    private fun transferElements(oldTable: Table<K, V>, newTable: Table<K, V>) {
        repeat(oldTable.capacity) { idx ->
            while (true) {
                when (val value = oldTable.values[idx]) {
                    is ConcurrentHashTable<*, *>.Fixed -> break
                    null -> {
                        // if the cell is empty, partially inserted or removed, then the value is null — just mark it as moved
                        if (oldTable.values.compareAndSet(idx, null, S)) {
                            break
                        }
                    }

                    else -> {
                        // the cell is not empty - replacing the value with a fixed value
                        if (oldTable.values.compareAndSet(idx, value, Fixed(value as V))) {
                            // transferring the key-value pair
                            // TODO is this enough, or do I need to claim the slot and copy the value as two separate operations here?
                            newTable.put(oldTable.keys[idx] as K, value)
                            // and marking the value as moved in the old table
                            oldTable.values[idx] = S
                            break
                        }
                    }

                }
            }
        }
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        val next = AtomicReference<Table<K, V>>(null)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            // TODO("Implement me!")

            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                while (true) {
                    val curKey = keys[index]
                    when (curKey) {
                        // The cell contains the specified key.
                        key -> {
                            // Update the value and return the previous one.
                            while (true) {
                                when (val oldValue = values[index]) {
                                    S -> return next.get().put(key, value)
                                    is ConcurrentHashTable<*, *>.Fixed -> {
                                        // helping
                                        next.get().put(key, oldValue.value as V)
                                        values[index] = S
                                        continue
                                    }

                                    else -> {
                                        if (values.compareAndSet(index, oldValue, value)) {
                                            return oldValue
                                        }

                                    }
                                }
                            }
                        }
                        // The cell does not store a key.
                        null -> {
                            // Insert the key/value pair into this cell.
                            if (keys.compareAndSet(index, null, key)) {
                                while (true) {
                                    when (val oldValue = values[index]) {
                                        S -> return next.get().put(key, value)
                                        is ConcurrentHashTable<*, *>.Fixed -> {
                                            // helping
                                            next.get().put(key, oldValue.value as V)
                                            values[index] = S
                                            continue
                                        }

                                        else -> {
                                            if (values.compareAndSet(index, oldValue, value)) {
                                                return oldValue
                                            }
                                        }
                                    }
                                }
                            } else {
                                // key has been set
                                continue
                            }
                        }

                        else -> {
                            break
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
            // TODO("Implement me!")
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
                        return when (val value = values[index]) {
                            S -> next.get().get(key)
                            is ConcurrentHashTable<*, *>.Fixed -> value.value as V?
                            else -> value as V?
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
            // TODO("Implement me!")

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

                        // Mark the slot available for `put(..)`,
                        // but do not stop on this cell when searching for a key.
                        // Read the value associated with the key and replace it with `null`.
                        while (true) {
                            when (val oldValue = values[index]) {
                                S -> return next.get().remove(key)
                                is ConcurrentHashTable<*, *>.Fixed -> {
                                    // helping
                                    next.get().put(key, oldValue.value as V)
                                    values[index] = S
                                    continue
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

    }

    object S

    inner class Fixed(val value: V)

}


private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()