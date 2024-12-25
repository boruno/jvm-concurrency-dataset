//package day4

import kotlinx.atomicfu.*
import kotlin.math.absoluteValue

class ConcurrentHashTableWithoutResize<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
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
        error("Should not be called in this task")
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)

        fun put(key: K, value: V): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace all writes to update key/value with CAS-s.
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        // Update the value and return the previous one.
                        while (true) {
                            val oldValue = values[index].value
                            if (values[index].compareAndSet(oldValue, value)) return oldValue
                        }
                    }
                    null -> {
                        while (true) {
                            if (keys[index].compareAndSet(null, key)) {
                                values[index].value = value
                                return null
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            return null
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`.
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> return values[index].value
                    null -> return null
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun remove(key: K): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace the write to update the value with CAS.
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        while (true) {
                            val oldValue = values[index].value
                            if (values[index].compareAndSet(oldValue, null)) return oldValue
                        }
                    }
                    null -> return null
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