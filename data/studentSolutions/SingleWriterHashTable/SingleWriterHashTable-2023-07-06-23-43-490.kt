@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.*

class SingleWriterHashTable<K: Any, V: Any> : HashTable<K, V> {
    private val core = atomic(Core<K, V>(INITIAL_SIZE))

    override fun put(key: K, value: V): V? {
        val putResult = core.value.put(key, value)
        return if (putResult === NEEDS_REHASH) {
            rehash()
            core.value.put(key, value) as V?
        } else {
            putResult as V?
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
        val newCore = Core<K, V>(curCore.size * 2)

        repeat(curCore.size) { index ->
            val key = curCore.keys[index].value
            val value = curCore.values[index].value
            if (key != null && value != null) {
                newCore.put(key, value)
            }
        }

        core.value = newCore
    }

    class Core<K: Any, V: Any>(
        val size: Int
    ) {
        val keys = atomicArrayOfNulls<K?>(size)
        val values = atomicArrayOfNulls<V?>(size)

        /**
         * Returns the previously stored value
         */
        fun put(key: K, value: V): Any? {
            var index = key.hashCode() % size
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
                index = (index + 1) % size
            }
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            var index = key.hashCode() % size
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
                index = (index + 1) % size
            }
        }

        fun remove(key: K): V? {
            var index = key.hashCode() % size
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
                index = (index + 1) % size
            }
        }
    }
}

private const val INITIAL_SIZE = 2
private const val PUT_ATTEMPTS_BEFORE_REHASH = 2
private val NEEDS_REHASH = "NEEDS_REHASH"