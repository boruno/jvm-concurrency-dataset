@file:Suppress("UNCHECKED_CAST")

//package day4

import kotlinx.atomicfu.*
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
        do {
            // Get a reference to the current table.
            val oldTable = table.value
            // Create a new table of x2 capacity.
            val newTable = Table<K, V>(oldTable.capacity * 2)

            // Try to set next. If not, help other thread
            oldTable.transferTable.compareAndSet(null, newTable)

            // Copy all elements from the current table to the new one.
            oldTable.drain()

            // Replace the current table with the new one.
        } while(!table.compareAndSet(oldTable, oldTable.transferTable.value!!))
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<K?>(capacity)
        val values = atomicArrayOfNulls<V?>(capacity)
        val transferTable = atomic<Table<K, V>?>(null)
        val transferIndex = atomic(0)

        fun put(key: K, value: V): Any? =
            applyMutation {
                var index = index(key)
                repeat(MAX_PROBES) {
                    if (keys[index].compareAndSet(null, key) || keys[index].value == key)
                        return@applyMutation Update.IndexUpdate(index, values[index].getAndSet(value))
                    index = (index + 1) % capacity
                }
                Update.NeedsRehash
            }.value

        fun get(key: K): V? {
            var index = index(key)
            repeat(MAX_PROBES) {
                if ((keys[index].value ?: return null) == key)
                    return values[index].value
                index = (index + 1) % capacity
            }
            return null
        }

        fun remove(key: K): V? =
            applyMutation {
                var index = index(key)
                repeat(MAX_PROBES) {
                    if (keys[index].value == key)
                        return@applyMutation Update.IndexUpdate(index, values[index].getAndSet(null))
                    index = (index + 1) % capacity
                }
                Update.Miss
            }.value as V?

        private fun applyMutation(op: () -> Update): Update {
            val change = op()
            return try {
                change
            } finally {
                // Carry updates over to transfer table
                if (change is Update.IndexUpdate) {
                    transferTable.value?.let { newTable ->
                        moveCell(change.index, newTable)
                    }
                }
            }
        }

        fun drain(): Boolean {
            while(true) {
                val newTable = transferTable.value ?: return false
                val index = transferIndex.getAndIncrement()
                if (index < capacity)
                    moveCell(index, newTable)
                else return true
            }
        }

        private fun moveCell(index: Int, newTable: Table<K, V>) {
            val key = keys[index].value
            val value = values[index].value
            if (key != null && value != null)
                newTable.put(key, value)
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

        private sealed class Update(val value: Any?) {
            object NeedsRehash : Update(NEEDS_REHASH)
            object Miss: Update(null)
            class IndexUpdate(val index: Int, value: Any?): Update(value)
        }
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()