@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table(initialCapacity))

    override fun put(key: K, value: V): V? = put(key, value, false, table.get())
    fun put(key: K, value: V, onlyNotInitialized: Boolean, startTable: Table): V? {
        var curTable = startTable
        while (true) {
            // Try to insert the key/value pair.
            val putResult = curTable.put(key, value, onlyNotInitialized = onlyNotInitialized)
            if (putResult === NEEDS_REHASH) {
                // The current table is too small to insert a new key.
                // Create a new table of x2 capacity,
                // copy all elements to it,
                // and restart the current operation.
                resize(curTable)
                curTable = curTable.next.get()!!
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

    private fun resize(curTable: Table) {
        val myNewTable = Table(capacity = curTable.capacity * 2)
        curTable.next.compareAndSet(null, myNewTable)
        applyStartedResize(curTable)
    }

    private fun applyStartedResize(curTable: Table) {
        val newTable = curTable.next.get()!!
        for (i in 0 until curTable.capacity) {
            moveValue(curTable, newTable, i)
        }
        table.compareAndSet(curTable, newTable)
    }

    private fun moveValue(
        curTable: Table,
        newTable: Table,
        i: Int,
    ) {
        fixValueAndMarkAsMovedIfEmpty(curTable, i)
        moveFixedValue(curTable, i, newTable)
    }

    private fun fixValueAndMarkAsMovedIfEmpty(curTable: Table, i: Int) {
        while (true) {
            val value = curTable.values[i]
            if (value is Fixed<*> || value === Moved) break
            val newValue = if (value == null || value === Removed) Moved else Fixed(value)
            if (curTable.values.compareAndSet(i, value, newValue)) break
        }
    }

    private fun moveFixedValue(
        curTable: Table,
        i: Int,
        newTable: Table,
    ) {
        val value = curTable.values[i]
        if (value is Fixed<*>) {
            val x = put(curTable.keys[i] as K, value.value as V, onlyNotInitialized = true, newTable)
            curTable.values[i] = Moved
        }
    }

    inner class Table(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)
        val next = AtomicReference<Table?>(null)
        
        private tailrec fun putOrDelete(key: K, value: Any?, onlyNotInitialized: Boolean, onProbesFail: Any?): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                val oldValue = values[index]
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        // Update the value and return the previous one.
                        return when {
                            onlyNotInitialized && oldValue != null -> oldValue
                            oldValue === Moved || oldValue is Fixed<*> -> {
                                applyStartedResize(this)
                                this.next.get()!!.putOrDelete(key, value, onlyNotInitialized, onProbesFail)
                            }

                            values.compareAndSet(index, oldValue, value) -> oldValue.convertToUserValue()

                            else -> putOrDelete(key, value, onlyNotInitialized, onProbesFail)
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        return when {
                            oldValue === Moved || oldValue is Fixed<*> -> {
                                applyStartedResize(this)
                                this.next.get()!!.putOrDelete(key, value, onlyNotInitialized, onProbesFail)
                            }

                            !keys.compareAndSet(index, null, key) -> putOrDelete(key, value, onlyNotInitialized, onProbesFail)
                            !values.compareAndSet(index, oldValue, value) -> putOrDelete(key, value, onlyNotInitialized, onProbesFail)

                            // No value was associated with the key.
                            else -> null
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // Inform the caller that the table should be resized.
            return onProbesFail
        }

        fun put(key: K, value: V, onlyNotInitialized: Boolean): Any? {
            return putOrDelete(key, value, onlyNotInitialized, NEEDS_REHASH)
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                val curValue = values[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        if (curValue === Moved || curValue is Fixed<*>) {
                            applyStartedResize(this)
                            return this@ConcurrentHashTable.get(key)
                        }
                        // Read the value associated with the key.
                        return curValue.convertToUserValue()
                    }
                    // Empty cell.
                    null -> {
                        if (curValue === Moved || curValue is Fixed<*>) {
                            applyStartedResize(this)
                            return this@ConcurrentHashTable.get(key)
                        }
                        // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        fun remove(key: K): V? {
            return putOrDelete(key, Removed, false, null) as V?
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue

        fun Any?.convertToUserValue(): V? = when {
            this == null -> null
            this === Moved -> error("Should be already handled")
            this === Removed -> null
            this is Fixed<*> -> error("Should be already handled")
            else -> this
        } as V?
    }
}

private class Fixed<V>(val value: V)

private val Moved = Any()
private val Removed = Any()

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()