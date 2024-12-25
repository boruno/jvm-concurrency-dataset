@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private class TableNode<K : Any, V : Any>(
        val table: Table<K, V>
    ) {
        val next = AtomicReference<TableNode<K, V>?>(null)


        fun resize() {
            val curNext = next.get()

            if (curNext != null) {
                moveElements(curNext)
            } else {
                createNext()
                moveElements(next.get()!!)
            }
        }

        fun moveElements(dst: TableNode<K, V>) {
            var allMoved = true
            for (index in 0 until table.capacity) {
                allMoved = allMoved and table.moveValue(dst.table, index)
            }

            if (!allMoved) {
                dst.createNext()
                moveElements(dst.next.get()!!)
            }
        }

        private fun createNext() {
            val newTable = Table<K, V>(table.capacity * 2)
            next.compareAndSet(null, TableNode(newTable))
        }
    }

    private val tableNode = AtomicReference(TableNode(Table<K, V>(initialCapacity)))
//    private val table = AtomicReference(Table<K, V>(initialCapacity))
//    private val nextTable: AtomicReference<Table<K, V>?> = AtomicReference(null)

    private fun resize(node: TableNode<K, V>) {
        node.resize()
        tableNode.compareAndSet(node, node.next.get())
    }

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val currentNode = tableNode.get()
            val putResult = currentNode.table.put(key, value)
            assert(putResult !is FixedValue<*> && putResult !is MovedValue)

            if (putResult === NEEDS_REHASH) {
                resize(currentNode)
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
            val curNode = tableNode.get()
            val value = curNode.table.get(key)

            when (value) {
                is FixedValue<*> -> {
                    return value.value as V?
                }

                is MovedValue -> {
                    resize(curNode)
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
            val curNode = tableNode.get()
            val oldValue = curNode.table.remove(key)

            when (oldValue) {
                is FixedValue<*> -> {
                    resize(curNode)
                    continue
                }

                is MovedValue -> {
                    resize(curNode)
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

        fun moveValue(otherTable: Table<K, V>, index: Int): Boolean {
            while (true) {
                val value = values[index]

                when (value) {
                    null -> {
                        if (values.compareAndSet(index, null, MovedValue)) {
                            return true
                        }
                    }

                    is RemovedValue -> {
                        if (values.compareAndSet(index, RemovedValue, MovedValue)) {
                            return true
                        }
                    }

                    is MovedValue -> {
                        return true
                    }

                    is FixedValue<*> -> {
                        val result = otherTable.putIfNew(keys[index] as K, value.value as V)
                        if (result == NEEDS_REHASH) {
                            return false
                        }
                        values.set(index, MovedValue)
                        return true
                    }

                    else -> {
                        if (values.compareAndSet(index, value, FixedValue(value))) {
                            val result = otherTable.putIfNew(keys[index] as K, value as V)
                            if (result == NEEDS_REHASH) {
                                return false
                            }
                            values.set(index, MovedValue)
                            return true
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