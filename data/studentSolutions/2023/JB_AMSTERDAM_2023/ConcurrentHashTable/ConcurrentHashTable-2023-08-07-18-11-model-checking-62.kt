@file:Suppress("UNCHECKED_CAST")

package day4

import kotlinx.atomicfu.*
import javax.swing.text.TabExpander
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = atomic(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            when (val putResult = table.value.put(key, value)) {
                NEEDS_REHASH -> resize()
                else         -> return putResult as V?
            }
        }
    }

    override fun get(key: K): V? {
        while (true) {
            when (val getResult = table.value.get(key)) {
                NEEDS_REHASH -> resize()
                else         -> return getResult as V?
            }
        }
    }

    override fun remove(key: K): V? {
        while (true) {
            when (val removeResult = table.value.remove(key)) {
                NEEDS_REHASH -> resize()
                else         -> return removeResult as V?
            }
        }
    }

    private fun resize() {
        val oldTable = table.value
        oldTable.moveToTheNextTable()
        table.compareAndSet(oldTable, oldTable.nextTable.value!!)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = atomicArrayOfNulls<Any?>(capacity)
        val values = atomicArrayOfNulls<Any?>(capacity)
        val nextTable : AtomicRef<Table<K,V>?> = atomic(null)
        val nextElementToBeMoved : AtomicInt = atomic(0)


        private fun moveNextElement () {
            // TODO()
            val curI = nextElementToBeMoved.value
            if (curI >= capacity) { return }
            val valueI = values[curI].value
            values[curI].compareAndSet(valueI, moved)
            when (val key = keys[curI].value) {
                moved -> { }
                null  -> { }
                else  -> {
//                    val newIndex = nextTable.value!!.index(key)
//                    when (val newValue = values[curI].value) {
//                        null -> keys[curI].compareAndSet(key, moved) // if (!keys[curI].compareAndSet(key, moved)) { return }
//                        else -> {
//                            nextTable.value?.put(key as K, newValue as V) // TODO: wrong
//                        }
//                    }
                    nextTable.value!!.put(key as K, valueI as V?) // TODO: wrong: moves several times
                    if (!keys[curI].compareAndSet(key, moved)) { return }
                }
            }
            nextElementToBeMoved.compareAndSet(curI, curI + 1)
        }

        fun moveToTheNextTable() {
            while (true) {
                when (val newTable = nextTable.value) {
                    null -> nextTable.compareAndSet(null, Table<K,V>(capacity * 2))
                    else -> break
                }
            }

            while (nextElementToBeMoved.value < capacity) {
                moveNextElement()
            }
        }

        fun put(key: K, value: V?): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            while (true) {
                var index = index(key)
                // Search for a specified key probing `MAX_PROBES` cells.
                // If neither the key nor an empty cell is found,
                // inform the caller that the table should be resized.
                repeat(MAX_PROBES) {
                    // Read the key.
                    val curKey = keys[index].value
                    when (curKey) {
                        moved -> return NEEDS_REHASH
                        key -> { // The cell contains the specified key. // Update the value and return the previous one.
                            while (true) {
                                when (val oldValue = values[index].value) {
                                    moved        -> NEEDS_REHASH // TODO: wrong
                                    NEEDS_REHASH -> TODO()
                                    else  -> if (values[index].compareAndSet(oldValue, value)) {
                                        return oldValue
                                    }
                                }
                            }
                        }
                        null -> { // The cell does not store a key. // Insert the key/value pair into this cell.
                            keys[index].compareAndSet(null, key)
                            return put(key, value)
                        }
                    }
                    // Process the next cell, use linear probing.
                    index = (index + 1) % capacity
                }
                // perform rehashing
                return NEEDS_REHASH
            }
        }

        fun get(key: K): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    moved -> {
                        return NEEDS_REHASH
                    }
                    key -> { // The cell contains the required key. // Read the value associated with the key.
                        return values[index].value as V?
                    }
                    null -> { // Empty cell. // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        fun remove(key: K): Any? {
            // TODO: Copy your implementation from `ConcurrentHashTableWithoutResize`
            // TODO: and add the logic related to moving key/value pairs to a new table.
            var index = index(key)
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index].value
                when (curKey) {
                    // The cell contains the required key.
                    moved -> return NEEDS_REHASH
                    key -> {
                        val oldValue = values[index].value
                        if (values[index].compareAndSet(oldValue, null)) {
                            return oldValue as V?
                        } else { return remove(key) }
                    }
                    null -> { // Empty cell. // The key has not been found.
                        return null
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            // The key has not been found.
            return null
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()
private val moved : Any = Any()