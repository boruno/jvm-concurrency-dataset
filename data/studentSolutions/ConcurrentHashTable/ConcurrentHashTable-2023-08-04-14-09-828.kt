@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReferenceArray
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
        return table.value.remove(key)
    }

    private fun resize() {
        // TODO:
    }

    private class Table<K : Any, V : Any>(val capacity: Int) {
        private val keys = AtomicReferenceArray<K?>(capacity)
        private val values = AtomicReferenceArray<V?>(capacity)

        fun get(key: K): V? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index]) {
                    null -> return null
                    key -> return values[index]
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun put(key: K, value: V): Any? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index]) {
                    null -> {
                        val witness = keys.compareAndExchange(index, null, key)
                        if (witness == null || witness == key) {
                            return values.getAndSet(index, value)
                        }
                    }

                    key -> return values.getAndSet(index, value)

                    else -> return NEEDS_REHASH // observed a non-equal key => not enough capacity for the collision chain
                }
                index = (index + 1) % capacity
            }
            return NEEDS_REHASH
        }

        fun remove(key: K): V? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index]) {
                    null -> return null
                    key -> return values.getAndSet(index, null)
                }
                index = (index + 1) % capacity
            }
            return null
        }
    }
}

private fun index(capacity: Int, key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()