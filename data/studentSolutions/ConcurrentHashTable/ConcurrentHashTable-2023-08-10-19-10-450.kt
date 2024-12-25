@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))
    private val nextTable: AtomicReference<Table<K, V>?> = AtomicReference(null)

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = table.get().put(key, value)
            assert(putResult !is FixedValue<*> && putResult !is MovedValue)

            if (putResult === NEEDS_REHASH) {
                resize()
            } else {
                if (putResult is RemovedValue) {
                    return null
                }
                return putResult as V?
            }
        }
    }

    override fun get(key: K): V? {
        while (true) {
            val curTable = table.get()
            val value = curTable.get(key)

            when (value) {
                is FixedValue<*> -> {
                    moveAndSet()
                    return value.value as V?
                }

                is MovedValue -> {
                    moveAndSet()
                    continue
                }

                is RemovedValue -> {
                    return null
                }

                else -> {
                    return value as V?
                }
            }
        }
    }

    override fun remove(key: K): V? {
        while (true) {
            val curTable = table.get()
            val oldValue = curTable.remove(key)

            when (oldValue) {
                is FixedValue<*> -> {
                    moveAndSet()
                    continue
                }

                is MovedValue -> {
                    moveAndSet()
                    continue
                }

                is RemovedValue -> {
                    return null
                }

                else -> {
                    return oldValue as V?
                }
            }
        }

    }

    private fun resize() {
        val curTable = table.get()
        val curNextTable = nextTable.get()

        if (curNextTable != null) {
            moveAndSet()
        } else {
            val newTable = Table<K, V>(curTable.capacity * 2)

            if (nextTable.compareAndSet(null, newTable)) {
                moveAndSet()
            }
        }
    }

    private fun moveAndSet() {
        val curTable = table.get()
        val curNextTable = nextTable.get()

        if (curNextTable != null) {
            curTable.moveElements(curNextTable)
            table.compareAndSet(curTable, curNextTable)
            nextTable.compareAndSet(curNextTable, null)
        }
    }


    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)

        fun put(key: K, value: V): Any? {
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
                        return setValueIfNotMoved(index, value)
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        val keyInserted = keys.compareAndSet(index, null, key)
                        if (keyInserted) {
                            return setValueIfNotMoved(index, value)
                        } else {
                            if (keys[index] == key) {
                                return setValueIfNotMoved(index, value)
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

        fun setValueIfNotMoved(index: Int, value: V): Any? {
            while (true) {
                val curValue = values[index]

                if (curValue is FixedValue<*> || curValue is MovedValue) {
                    return NEEDS_REHASH
                }

                if (values.compareAndSet(index, curValue, value)) {
                    return curValue
                }
            }
        }

        fun get(key: K): Any? {
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
                        return values[index]
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

        fun remove(key: K): Any? {
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
                        // Read the value associated with the key and replace it with `null`.
                        return removeIfNotMoved(index)
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

        fun removeIfNotMoved(index: Int): Any? {
            while (true) {
                val curValue = values.get(index)

                if (curValue is FixedValue<*> || curValue is MovedValue) {
                    return curValue
                }

                if (values.compareAndSet(index, curValue, RemovedValue)) {
                    return curValue
                }
            }
        }


        fun moveElements(otherTable: Table<K, V>) {
            if (otherTable === this) return

            repeat(capacity) { index ->
                moveValue(otherTable, index)
            }
        }

        fun moveValue(otherTable: Table<K, V>, index: Int) {
            while (true) {
                val value = values[index]

                when (value) {
                    null -> {
                        if (values.compareAndSet(index, null, MovedValue)) {
                            return
                        }
                    }

                    is RemovedValue -> {
                        if (values.compareAndSet(index, RemovedValue, MovedValue)) {
                            return
                        }
                    }

                    is MovedValue -> {
                        return
                    }

                    is FixedValue<*> -> {
                        val result = otherTable.putIfNew(keys[index] as K, value.value as V)
                        assert(result !== NEEDS_REHASH)
                        values.set(index, MovedValue)
                        return
                    }

                    else -> {
                        if (values.compareAndSet(index, value, FixedValue(value))) {
                            val result = otherTable.putIfNew(keys[index] as K, value as V)
                            assert(result !== NEEDS_REHASH)
                            values.set(index, MovedValue)
                            return
                        }
                    }
                }
            }
        }

        fun putIfNew(key: K, value: V): Any? {
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
                        values.compareAndSet(index, null, value)
                        return null
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        val keyInserted = keys.compareAndSet(index, null, key)
                        if (keyInserted) {
                            values.compareAndSet(index, null, value)
                            return null
                        }
                        if (keys[index] == key) {
                            values.compareAndSet(index, null, value)
                            return null
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }

    data class FixedValue<V>(val value: V?)
    object MovedValue
    object RemovedValue
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()