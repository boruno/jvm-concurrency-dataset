@file:Suppress("UNCHECKED_CAST")

//package day4

import java.util.concurrent.atomic.*
import kotlin.math.absoluteValue

class ConcurrentHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val head = AtomicReference(Table<K, V>(initialCapacity))
    private val tail = AtomicReference(head.get())

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            val putResult = head.get().put(key, value)
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
        return head.get().get(key)
    }

    override fun remove(key: K): V? {
        while (true) {
            val putResult = head.get().remove(key)
            if (putResult === NEEDS_REHASH) {
                resize()
            } else {
                // The operation has been successfully performed,
                // return the previous value associated with the key.
                return putResult as V?
            }
        }
    }

    private fun resize() {
        // add new table to the queue
        while (true) {
            val curTail = tail.get()
            if (curTail.state.get() == TableState.LIVE) {
                break
            }
            val newTail = Table<K, V>(2 * curTail.capacity)
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                break
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
            }
        }

        // copy elements
        val tab = head.get()
        if (tab.state.get() == TableState.REHASHING) {
            val nextTab = tab.next.get()!!
            for (idx in 0 until tab.capacity) {
                while (true) {
                    val key = tab.keys[idx]
                    when (key) {
                        null -> {
                            if (tab.values.compareAndSet(idx, null, S)) {
                                // mark absent value as moved, no need to actually move it
                                break
                            }
                        }
                        else -> {
                            val curVal = tab.values.get(idx)
                            when (curVal) {
                                null -> {
                                    if (tab.values.compareAndSet(idx, null, S)) {
                                        // key and no val, mark as copied
                                        break
                                    }
                                }
                                S -> {
                                    // someone already copied it
                                    break
                                }
                                else -> {
                                    val copying = if (curVal is ConcurrentHashTable<*, *>.Copying) {
                                        // someone started copy, we will help finishing it
                                        curVal
                                    } else {
                                        // try locking value
                                        val copying = Copying(tab, nextTab, idx, key as K, curVal as V)
                                        if (tab.values.compareAndSet(idx, curVal, copying)) {
                                            copying
                                        } else {
                                            // failed to lock, repeat in the loop
                                            continue
                                        }
                                    }
                                    copying.finish()
                                }
                            }
                        }
                    }
                }
            }
            for (idx in 0 until tab.capacity) {
                require(tab.values[idx] == S)
            }
            tab.state.set(TableState.COPIED)
        }

        // move head
        while (true) {
            val curHead = head.get()
            val newHead = curHead.next.get()
            if (newHead == null || curHead.state.get() != TableState.COPIED) {
                break
            }
            if (head.compareAndSet(curHead, newHead)) {
                break
            }
        }
    }

    object S

    inner class Copying(
        val tab: Table<K, V>,
        val nextTab: Table<K, V>,
        val oldIdx: Int,
        val key: K,
        val value: Any
    ) {
        fun finish() {
            val nextTabOldVal = nextTab.put(key, value as V)

            // we either put value in empty slot, or some other thread put our value
            //require(nextTabOldVal == null || nextTabOldVal == value)

            // either we or other thread marks slot as copied
            if (!tab.values.compareAndSet(oldIdx, this, S)) {
                require(tab.values.get(oldIdx) == S)
            }
        }
    }

    enum class TableState {
        LIVE, REHASHING, COPIED
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val next = AtomicReference<Table<K, V>?>(null)

        val state = AtomicReference(TableState.LIVE)

        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<Any?>(capacity)

        fun put(key: K, value: V): Any? {
            if (state.get() != TableState.LIVE) {
                return NEEDS_REHASH
            }

            var index = indexForKey(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys.get(index)
                when (curKey) {
                    // The cell contains the specified key.
                    key -> {
                        val v = values.get(index)
                        when (v) {
                            S -> {
                                return NEEDS_REHASH
                            }
                            is ConcurrentHashTable<*, *>.Copying -> {
                                return NEEDS_REHASH
                            }
                            else -> {
                                val witness = values.compareAndExchange(index, v, value)
                                if (witness == S || witness is ConcurrentHashTable<*, *>.Copying) {
                                    return NEEDS_REHASH
                                }
                                return witness as V?
                            }
                        }
                    }
                    // The cell does not store a key.
                    null -> {
                        // Insert the key/value pair into this cell.
                        keys.compareAndSet(index, null, key)
                        if (keys.get(index) == key) {
                            val v = values.get(index)
                            when (v) {
                                S -> {
                                    return NEEDS_REHASH
                                }
                                is ConcurrentHashTable<*, *>.Copying -> {
                                    return NEEDS_REHASH
                                }
                                else -> {
                                    val witness = values.compareAndExchange(index, v, value)
                                    if (witness == S || witness is ConcurrentHashTable<*, *>.Copying) {
                                        return NEEDS_REHASH
                                    }
                                    return witness as V?
                                }
                            }
                        }
                    }
                }
                // Process the next cell, use linear probing.
                index = (index + 1) % capacity
            }
            state.set(TableState.REHASHING)
            return NEEDS_REHASH
        }

        fun get(key: K): V? {
            var index = indexForKey(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys.get(index)
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        val v = values.get(index)
                        when (v) {
                            S -> {
                                // value was moved to the next table
                                val nextTab = next.get()
                                require(nextTab != null)
                                return nextTab.get(key)
                            }
                            is ConcurrentHashTable<*, *>.Copying -> {
                                // value is being copied, assume all writers help rehash,
                                // wrapped value should be returned until copy is complete
                                return v.value as V
                            }
                            else -> {
                                return v as V?
                            }
                        }
                    }
                    // Empty cell.
                    null -> {
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

        fun remove(key: K): Any? {
            var index = indexForKey(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    // The cell contains the required key.
                    key -> {
                        val v = values.get(index)
                        when (v) {
                            S -> {
                                return NEEDS_REHASH
                            }
                            is ConcurrentHashTable<*, *>.Copying -> {
                                return NEEDS_REHASH
                            }
                            else -> {
                                values.compareAndSet(index, v, null)
                                return v
//                                val witness = values.compareAndExchange(index, v, null)
//                                if (witness == S || witness is ConcurrentHashTable<*, *>.Copying) {
//                                    return NEEDS_REHASH
//                                }
//                                return witness as V?
                            }
                        }

//                        while (true) {
//                            val v = values.get(index)
//                            when (v) {
//                                S -> {
//                                    // value was moved to the next table
//                                    val nextTab = next.get()
//                                    require(nextTab != null)
//                                    return nextTab.remove(key)
//                                }
//                                is ConcurrentHashTable<*, *>.Copying -> {
//                                    // help finish, and then remove in the next table
//                                    v.finish()
//                                    val nextTab = next.get()
//                                    require(nextTab != null)
//                                    return nextTab.remove(key)
//                                }
//                                else -> {
//                                    val witness = values.compareAndExchange(index, v, null)
//                                    if (witness == S || witness is ConcurrentHashTable<*, *>.Copying) {
//                                        // rehashing started, try again
//                                        continue
//                                    }
//                                    return witness as V?
//                                }
//                            }
//                        }
                    }
                    // Empty cell.
                    null -> {
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

        fun indexForKey(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2
private val NEEDS_REHASH = Any()