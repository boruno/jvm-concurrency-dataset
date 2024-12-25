@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
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
        val curCore = table.get()
        val newTable = Table<K, V>(curCore.capacity * 2)
        repeat(curCore.capacity) { index ->
            val key = curCore.keys[index]
            val value = curCore.values[index]
            if (key != null && value != null) {
                newTable.put(key as K, value)
            }
        }
        table.set(newTable)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index]
                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        if (!values.compareAndSet(index, oldValue, value)) return put(key, value)
                        return oldValue
                    }

                    null -> {
                        if (!keys.compareAndSet(index, null, key)) return put(key, value)
                        if (!values.compareAndSet(index, null, value)) return put(key, value)
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
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
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index]
                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        if (!values.compareAndSet(index, oldValue, null)) return remove(key)
                        return oldValue
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