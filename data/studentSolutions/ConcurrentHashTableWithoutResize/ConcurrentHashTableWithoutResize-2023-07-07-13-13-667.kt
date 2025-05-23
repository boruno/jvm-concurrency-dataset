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

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace all writes to update key/value with CAS-s.
//            TODO("Implement me!")
            var index = index(key)
            while (true) {
                var cell = keys[index].value
                when {
                    cell == null -> keys[index].compareAndSet(cell, key)
                    cell != key -> index ++
                    else -> values[index].getAndSet(value)
                }
            }
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`.
//            TODO("Implement me!")
            var index = index(key)
            while (true) {
                val cell = keys[index].value
                when {
                    cell == null -> return null
                    cell != key -> index ++
                    else -> return values[index].value
                }
            }
        }

        fun remove(key: K): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace the write to update the value with CAS.
//            TODO("Implement me!")
            var index = index(key)
            while (true) {
                val cell = keys[index].value
                when {
                    cell == null -> return null
                    cell != key -> index ++
                    else -> return values[index].getAndSet(null)
                }
            }

        }
        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()