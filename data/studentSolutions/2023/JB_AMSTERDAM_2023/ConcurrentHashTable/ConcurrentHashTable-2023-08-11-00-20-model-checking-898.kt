@file:Suppress("UNCHECKED_CAST")

package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun get(key: K): V? {
        return table.get().get(key)
    }

    override fun put(key: K, value: V): V? {
        return performWithResize { it.put(key, value) }
    }

    override fun remove(key: K): V? {
        return performWithResize { it.remove(key) }
    }

    private fun performWithResize(action: (Table<K, V>) -> Any?): V? {
        while (true) {
            // Try to insert the key/value pair.
            val curTable = table.get()
            val res = action(curTable)
            if (res === NEEDS_REHASH) {
                // Resize and  restart the current operation.
                resize(curTable)
            } else {
                // The operation has been successfully performed!
                return res as V?
            }
        }
    }

    private val resizeLock = AtomicBoolean()

    private fun resize(curTable: Table<K, V>) {

        while (true) {
            if (resizeLock.compareAndSet(false, true)) {
                try {
                    if (curTable != table.get()) {
                        return
                    }

                    // Our motto: no fast-paths!

                    // Create a new table of x2 capacity and try to be the first one to install it.
                    Table<K, V>(curTable.capacity * 2).let {
                        curTable.nextTable.compareAndSet(null, it)
                    }

                    // It could be our new table or somebody else's one.
                    // Copy values regardless of this uncertainty.
                    val nextTable = curTable.nextTable.get()

                    // Copy all not-yet copied elements from the current table to the new one.
                    var idx = 0
                    while (idx < curTable.values.length()) {
                        val value = curTable.values[idx]
                        val key = curTable.keys[idx]
                        when (value) {
                            // The cell is done.
                            MOVED_MARKER -> {}

                            // Trivial move of empty cell: just mark it.
                            null ->
                                if (!curTable.values.compareAndSet(idx, null, MOVED_MARKER)) {
                                    // Try to copy this cell one more time, somebody modified it.
                                    continue
                                }

                            !is FixWrapper<*> -> {
                                // First step of non-trivial moving: fix value to prevent its modification.
                                val wrapper = FixWrapper(value)
                                if (!curTable.values.compareAndSet(idx, value, wrapper)) {
                                    // Try to copy this cell one more time, somebody modified it.
                                    continue
                                }
                            }

                            else -> {
                                // Second step of non-trivial moving: put value to new table and mark the cell.
                                val unwrappedValue = (value as FixWrapper<V>).value
                                nextTable.put(key!!, unwrappedValue).let {
                                    assert(it == null || it == unwrappedValue)
                                }
                                curTable.values.compareAndSet(idx, value, MOVED_MARKER)
                            }
                        }
                        idx++
                    }

                    // Replace current table with the new one if we are lucky enough.
                    assert(table.get().let { it == curTable || it == nextTable })
                    table.compareAndSet(curTable, nextTable).let {
                        assert(it)
                    }
                } finally {
                    resizeLock.set(false)
                }
            }
        }
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<K?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        val nextTable = AtomicReference<Table<K, V>>(null)

        fun get(key: K): V? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            iterateCells(key) { index ->
                // Read the key.
                when (keys[index]) {
                    // The cell contains the required key.
                    key -> {
                        // Read the value associated with the key.
                        return when (val value = values[index]) {
                            // It's moved to new table, get it there.
                            MOVED_MARKER -> nextTable.get().get(key)
                            // It's in process of movement, but we still have fixed copy here.
                            is FixWrapper<*> -> (value as FixWrapper<V>).value
                            // Just a value which doesn't try to move anywhere.
                            else -> value as V?
                        }
                    }
                    // Empty cell.
                    null -> {
                        // The key has not been found.
                        return null
                    }
                    // Skip different keys.
                }
            }
            // The key has not been found.
            return null
        }

        fun put(key: K, value: V): Any? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            iterateCells(key) { index ->
                while (true) {
                    when (keys[index]) {
                        // The cell contains the specified key.
                        key -> {
                            val curValue = values[index]
                            if (curValue === MOVED_MARKER || curValue is FixWrapper<*>) {
                                // Oops, this cell has moved.
                                return NEEDS_REHASH
                            }

                            // Update the value and return the previous one.
                            if (values.compareAndSet(index, curValue, value)) {
                                return curValue
                            }
                        }
                        // The cell does not store a key.
                        null -> {
                            val curValue = values[index]
                            if (curValue === MOVED_MARKER || curValue is FixWrapper<*>) {
                                // Oops, this cell has moved.
                                return NEEDS_REHASH
                            }

                            // Insert the key/value pair into this cell.
                            if (keys.compareAndSet(index, null, key)) {
                                if (values.compareAndSet(index, curValue, value)) {
                                    return curValue
                                }
                            }
                        }
                        // The cell contains different key.
                        else -> break
                    }
                }
            }
            // Inform the caller that the table should be resized.
            return NEEDS_REHASH
        }

        fun remove(key: K): Any? {
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            iterateCells(key) { index ->
                while (true) {
                    when (keys[index]) {
                        // The cell contains the required key.
                        key -> {
                            val curValue = values[index]
                            if (curValue === MOVED_MARKER || curValue is FixWrapper<*>) {
                                // Oops, this cell has moved.
                                return NEEDS_REHASH
                            }

                            // Replace the value with `null`.
                            // Leave key non-null: yes, we grab this cell forever for simplicity.
                            if (values.compareAndSet(index, curValue, null)) {
                                return curValue
                            }
                        }
                        // Empty cell.
                        null -> {
                            // The key has not been found.
                            return null
                        }
                        // Skip different keys.
                        else -> break
                    }
                }
            }
            // The key has not been found.
            return null
        }

        private inline fun iterateCells(key: K, process: (Int) -> Unit) {
            var index = index(key)
            repeat(MAX_PROBES) {
                process(index)
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()
private val MOVED_MARKER = Any()
private class FixWrapper<V>(val value: V)