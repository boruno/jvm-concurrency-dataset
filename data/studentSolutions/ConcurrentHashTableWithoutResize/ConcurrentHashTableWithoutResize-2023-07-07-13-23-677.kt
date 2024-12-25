//package day4

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.math.absoluteValue

class ConcurrentHashTableWithoutResize<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            val putResult = table.value.put(key, value)
            if (putResult === NEEDS_REHASH) {
                resize()
            } else {
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
            var index = index(key)
            while (true) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        val oldValue = values[index].value
                        if (values[index].compareAndSet(oldValue, value)) {
                            return oldValue
                        }
                    }

                    null -> {
                        if (keys[index].compareAndSet(null, key)) {
                            return values[index].getAndSet(value)
                        }
                    }
                }
                index = (index + 1) % capacity
            }
        }

        fun get(key: K): V? {
            var index = index(key)
            repeat(MAX_PROBES) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        return values[index].value
                    }

                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun remove(key: K): V? {
            var index = index(key)
            while (true) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        val oldValue = values[index].value
                        if (values[index].compareAndSet(oldValue, null)) {
                            return oldValue
                        }
                    }

                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()