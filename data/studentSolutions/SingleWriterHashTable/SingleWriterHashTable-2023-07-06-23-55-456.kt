@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.*

class SingleWriterHashTable<K: Any, V: Any> : HashTable<K, V> {
    private val core = atomic(Core<K, V>(INITIAL_CAPACITY))

    override fun put(key: K, value: V): V? {
        while (true) {
            val putResult = core.value.put(key, value)
            if (putResult === NEEDS_REHASH) {
                rehash()
            } else {
                return putResult as V?
            }
        }
    }

    override fun get(key: K): V? {
        return core.value.get(key)
    }

    override fun remove(key: K): V? {
        return core.value.remove(key)
    }

    fun rehash() {
        val curCore = core.value
        val newCore = Core<K, V>(curCore.capacity * 2)

        repeat(curCore.capacity) { index ->
            val key = curCore.keys[index].value
            val value = curCore.values[index].value
            if (key != null && value != null) {
                newCore.put(key, value)
            }
        }

        core.value = newCore
    }

    class Core<K: Any, V: Any>(
        val capacity: Int
    ) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)

        /**
         * Returns the previously stored value
         */
        fun put(key: K, value: V): Any? {
            var index = key.hash % capacity
            repeat(PUT_ATTEMPTS_BEFORE_REHASH) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        val curValue = values[index].value
                        values[index].value = value
                        return curValue
                    }
                    null -> {
                        keys[index].value = key
                        values[index].value = value
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            var index = key.hash % capacity
            while(true) {
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
        }

        fun remove(key: K): V? {
            var index = key.hash % capacity
            while(true) {
                val curKey = keys[index].value
                when (curKey) {
                    key -> {
                        val curValue = values[index].value
                        values[index].value = null
                        return curValue
                    }
                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
        }
    }
}

private val Any.hash: Int get() = hashCode() * MAGIC

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 4
private const val PUT_ATTEMPTS_BEFORE_REHASH = 5
private val NEEDS_REHASH = "NEEDS_REHASH"