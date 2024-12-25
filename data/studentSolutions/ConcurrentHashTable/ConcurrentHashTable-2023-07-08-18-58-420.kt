@file:Suppress("UNCHECKED_CAST")

//package day4

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
        return table.value.get(key) as V?
    }

    override fun remove(key: K): V? {
        return table.value.remove(key) as V?
    }

    private fun resize() {
        val currentTable = table.value
        currentTable.moveElements()
        table.compareAndSet(currentTable, currentTable.next.value!!)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<Any?>(capacity)
        val next: AtomicRef<Table<K, V>?> = atomic(null)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return values[index].getAndSet(value)
                    }
                    // The cell does not store a key.
                    null -> {
                        keys[index].compareAndSet(null, key)
                        return put(key, value)
                    }
                    is Frozen, Moved -> {
                        moveElements()
                        return next.value!!.put(key, value)
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
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return values[index].value as V?
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    is Frozen -> return values[index].value as V?
                    is Moved -> {
                        moveElements()
                        return next.value!!.get(key)
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
                        return values[index].getAndSet(null) as V?
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    is Frozen, Moved -> {
                        moveElements()
                        return next.value!!.remove(key)
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        fun moveElements() {
            val newTable = Table<K, V>(capacity * 2)
            next.compareAndSet(null, newTable)
            val nextTable = next.value!!

            for (i in 0 until capacity) {
                val v = values[i].value
                v?.let {
                    when (it) {
                        Moved -> {} // do nothing
                        is Frozen -> {
                            val k = keys[i].value!!
                            val v = it.value as V
                            val putResult = nextTable.put(k, v)
                            if (putResult == NEEDS_REHASH) {
                                nextTable.moveElements()
                                nextTable.put(k, v)
                            }
                            values[i].compareAndSet(v, Moved)
                        }
                        else -> values[i].compareAndSet(v, Frozen(v))
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
class Frozen(val value: Any)
object Moved