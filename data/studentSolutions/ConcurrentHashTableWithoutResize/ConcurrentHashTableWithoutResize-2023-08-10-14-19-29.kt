@file:Suppress("UNCHECKED_CAST", "DuplicatedCode")

package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.absoluteValue

class ConcurrentHashTableWithoutResize<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table: AtomicReference<Table<K, V>> = AtomicReference(Table(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            val putResult = table.get().put(key, value)
            if (putResult === NEEDS_REHASH) {
                resize()
            } else {
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
//                        val oldValue = values[index]
//                        values[index] = value
                        return values.getAndSet(index, value)
                    }

                    null -> {
                        keys.set(index, key)
                        values.set(index, value)
//                        keys[index] = key
//                        values[index] = value
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
                val curKey = keys[index]
                when (curKey) {
                    key -> return values[index]
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
//                        val oldValue = values[index]
//                        values[index] = null
//                        return oldValue
                        return values.getAndSet(index, null)
                    }

                    null -> return null

                }
                index = (index + 1) % capacity
            }
            return null
        }

        private fun index(key: Any): Int = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()