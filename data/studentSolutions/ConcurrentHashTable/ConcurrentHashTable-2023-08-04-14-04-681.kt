@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
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
        private val keys = atomicArrayOfNulls<K?>(capacity)
        private val values = atomicArrayOfNulls<V?>(capacity)

        fun get(key: K): V? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index].value) {
                    null -> return null
                    key -> return values[index].value
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun put(key: K, value: V): Any? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index].value) {
                    null -> {
                        if (keys[index].compareAndSet(null, key) || keys[index].value == key) {
                            return values[index].getAndSet(value)
                        }
                    }

                    key -> return values[index].getAndSet(value)
                    else -> Unit
                }
                index = (index + 1) % capacity
            }
            return NEEDS_REHASH
        }

        fun remove(key: K): V? {
            var index = index(capacity, key)
            repeat(MAX_PROBES) {
                when (keys[index].value) {
                    null -> return null
                    key -> return values[index].getAndSet(null)
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