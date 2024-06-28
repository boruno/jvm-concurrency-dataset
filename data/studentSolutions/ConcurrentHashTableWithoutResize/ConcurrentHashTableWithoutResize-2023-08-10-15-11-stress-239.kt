package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTableWithoutResize<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    @Suppress("UNCHECKED_CAST")
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
        error("Should not be called in this task")
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `SingleWriterHashTable`
            // TODO: and replace all writes to update key/value with CAS-s.
            var index = index(key)

            repeat(MAX_PROBES) {
                val curKey = keys[index]

                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        if (values.compareAndSet(index, oldValue, value)) {
                            return oldValue
                        }
                    }

                    null -> {
                        return if (keys.compareAndSet(index, null, key)) {
                            if (values.compareAndSet(index, null, value)) null
                            else put(key, value)
                        } else put(key, value)
                    }
                }

                index = (index + 1) % capacity
            }

            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `SingleWriterHashTable`.
            var index = index(key)

            repeat(MAX_PROBES) {
                val curKey = keys[index]

                when (curKey) {
                    key -> values[index]?.let { return it }
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
                val curKey = keys[index]

                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        if (values.compareAndSet(index, oldValue, null)) {
                            return oldValue
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