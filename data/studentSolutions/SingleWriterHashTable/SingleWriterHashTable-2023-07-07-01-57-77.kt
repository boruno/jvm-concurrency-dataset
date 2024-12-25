@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.*
import kotlin.math.*

class SingleWriterHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
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
        val curCore = table.value
        // Create a new table of x2 capacity.
        val newTable = Table<K, V>(curCore.capacity * 2)
        // Copy all elements from the current table to the new one.
        repeat(curCore.capacity) { index ->
            val key = curCore.keys[index].value
            val value = curCore.values[index].value
            // Is the cell non-empty and does a value present?
            if (key != null && key != REMOVED_KEY && value != null) {
                newTable.put(key as K, value)
            }
        }
        // Replace the current table with the new one.
        table.value = newTable
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<Any?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        val curValue = values[index].value
                        values[index].value = value
                        return curValue
                    }

                    null, REMOVED_KEY -> {
                        keys[index].value = key
                        values[index].value = value
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        return values[index].value
                    }

                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun remove(key: K): V? {
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        keys[index].value = REMOVED_KEY
                        val curValue = values[index].value
                        values[index].value = null
                        return curValue
                    }

                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return null
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()

private val REMOVED_KEY = Any()