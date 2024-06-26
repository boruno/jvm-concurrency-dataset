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
        return table.value.remove(key) as V?
    }

    private fun resize() {
        val currentTable = table.value
        val newTable = Table<K, V>(currentTable.capacity * 2)
        if (currentTable.nextTable.compareAndSet(null, newTable)) { // single resizer
            for (index in 0 until currentTable.capacity) {
                while (true) {
                    when (val key = currentTable.keys[index].value) {
                        null -> {
                            if (currentTable.values[index].compareAndSet(null, MOVED)) {
                                break
                            }
                        }
                        else -> {
                            val value = currentTable.values[index].value
                            if (value != null) {
                                newTable.put(key, value as V)
                            }
                            if (currentTable.values[index].compareAndSet(value, MOVED)) {
                                break
                            }
                        }
                    }
                }
            }
            currentTable.moveFinished.value = true
        }

        while (true) {
            val curTable = table.value
            if (curTable.moveFinished.value) {
                table.compareAndSet(curTable, curTable.nextTable.value!!)
            }
            else {
                break
            }
        }
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val nextTable = atomic<Table<K, V>?>(null)
        val moveFinished = atomic<Boolean>(false)
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<Any?>(capacity)

        fun put(key: K, value: V): Any? {
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
                        return setValue(index, value).let { oldResult ->
                            if (oldResult == MOVED)
                                nextTable.value!!.put(key, value)
                            else
                                oldResult
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        if (keys[index].compareAndSet(null, key)
                            // concurrent setting fof the same key, use the same cell
                            || keys[index].value == key
                        ) {
                            return setValue(index, value).let { oldResult ->
                                if (oldResult == MOVED)
                                    nextTable.value!!.put(key, value)
                                else
                                    oldResult
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
        }

        private fun setValue(index: Int, value: V?): Any? {
            while (true) {
                val oldValue = values[index].value
                if (oldValue == MOVED)
                    return MOVED
                if (values[index].compareAndSet(oldValue, value)) {
                    return oldValue as V?
                }
            }
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
                        return values[index].value.let { result ->
                            if (result == MOVED)
                                nextTable.value!!.get(key)
                            else
                                result as V?
                        }
                    }
                    null -> {
                        return if (values[index].value == MOVED)
                            nextTable.value!!.get(key)
                        else
                            null
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
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        return setValue(index, null).let { oldResult ->
                            if (oldResult == MOVED)
                                nextTable.value!!.remove(key)
                            else
                                oldResult as V?
                        }
                    }
                    null -> {
                        return if (values[index].value == MOVED)
                            nextTable.value!!.remove(key)
                        else
                            null
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

}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()

private val MOVED = Any()