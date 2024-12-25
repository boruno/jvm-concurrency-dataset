//package mpp.dynamicarray

import kotlinx.atomicfu.*

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < size)
        var currentCore = core.value
        while (true) {
            if (currentCore.capacity <= index) {
                currentCore = currentCore.next.value!!
                continue
            }

            @Suppress("UNCHECKED_CAST")
            return when (val cellValue = currentCore.array[index].value) {
                is Moved -> {
                    currentCore = currentCore.next.value!!
                    continue
                }

                is Fixed<*> -> (cellValue as Fixed<E>).value
                else -> cellValue as E
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        var currentCore = core.value
        while (currentCore.capacity <= index)
            currentCore = currentCore.next.value!!
        putInternal(currentCore, index, element)
    }

    private fun putInternal(startCore: Core<E>, index: Int, element: E) {
        setInternal(startCore, index, element, weak = false)
    }

    private fun moveInternal(startCore: Core<E>, index: Int, element: E) {
        setInternal(startCore, index, element, weak = true)
    }
    private fun setInternal(startCore: Core<E>, index: Int, element: E, weak: Boolean) {
        var currentCore = startCore
        while (true) {
            val cell = currentCore.array[index]

            when (val cellValue = cell.value) {
                is Moved -> {
                    currentCore = currentCore.next.value!!
                }

                is Fixed<*> -> {
                    val nextCore = currentCore.next.value!!
                    moveFixed(cell, nextCore, index, cellValue)
                    currentCore = nextCore
                }

                else -> {
                    if (weak) {
                        cell.compareAndSet(null, element)
                        break
                    } else if (cell.compareAndSet(cellValue, element)) {
                        break
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var currentCore = core.value
        while (true) {
            val sz = size
            val cp = currentCore.capacity

            if (sz >= cp) {
                if (currentCore.next.value == null) {
                    val nextCore = Core<E>(2 * cp)
                    if (currentCore.next.compareAndSet(null, nextCore))
                        transfer(currentCore)
                }
                currentCore = currentCore.next.value!!
            } else if (currentCore.array[sz].compareAndSet(null, element)) {
                _size.compareAndSet(sz, sz + 1)
                break
            } else {
                _size.compareAndSet(sz, sz + 1) // helping
            }
        }
    }

    private fun transfer(currentCore: Core<E>) {
        val nextCore = currentCore.next.value!!
        for (i in 0 until currentCore.capacity) {
            val cell = currentCore.array[i]

            while (true) {
                when (val cellValue = cell.value) {
                    is Moved -> break
                    is Fixed<*> -> {
                        moveFixed(cell, nextCore, i, cellValue)
                        break
                    }

                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        val element = cellValue as E
                        if (cell.compareAndSet(element, Fixed(element))) {
                            moveFixed(cell, nextCore, i, Fixed(element))
                            break
                        }
                    }
                }
            }
        }
        core.compareAndSet(currentCore, nextCore)
    }

    @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
    private inline fun moveFixed(
        currentCell: AtomicRef<Any?>, nextCore: Core<E>,
        index: Int, cellValue: Any
    ) {
        moveInternal(nextCore, index, (cellValue as Fixed<E>).value)
        currentCell.value = Moved
    }

    private val _size = atomic(0)
    override val size: Int get() = _size.value
}

private class Fixed<E>(val value: E)
private object Moved

private class Core<E>(
    val capacity: Int
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    val next = atomic<Core<E>?>(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME