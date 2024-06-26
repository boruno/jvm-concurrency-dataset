package mpp.dynamicarray

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

    val capacity: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val newCore: AtomicRef<Core<E>?> = atomic(null)
    private val migrationStatus = atomic<Boolean>(false)

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < core.value.getSize())
        if (!migrationStatus.value) {
            if (!core.value.tryPut(index, element)) {
                while (true) {
                    if (!migrationStatus.value) {
                        core.value.tryPut(index, element)
                        return
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var oldCore = core.value
        migrationStatus.compareAndSet(expect = false, update = true)
        newCore.compareAndSet(null, Core(oldCore.maxSize * 2))
        newCore.value?.trySetSize(0, oldCore.getSize())

        for (i in 0 until oldCore.getSize()) {
            if (core.value.tryLock(i)) {
                newCore.value?.tryPlace(i, get(i))
            }
        }

        if (newCore.value?.tryPlace(oldCore.getSize(), element) == true) {

        } else {
            while (true) {
                if (!migrationStatus.value || newCore.value == null) {
                    val place = core.value.getSize()
                    if (core.value.tryPlace(place, element)) {
                        break
                    }
                } else {
                    val place = newCore.value?.getSize() ?: continue
                    if (newCore.value?.tryPlace(place, element) == true) {
                        break
                    }
                }
            }
        }
        oldCore = core.value
        newCore.value?.let { core.compareAndSet(oldCore, it) }
        newCore.compareAndSet(newCore.value, null)
        migrationStatus.compareAndSet(expect = true, update = false)
    }

    override val size: Int get() = core.value.getSize()

    override val capacity: Int get() = core.value.maxSize
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<Cell<E>>(capacity)
    private val _size = atomic(0)
    val maxSize: Int = capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        return array[index].value?.getValue() as E
    }

    fun tryPut(index: Int, element: E): Boolean {
        require(index < _size.value)
        val prev = array[index].value
        if (prev != null) {
            if (prev.moved.value) {
                return false
            }
        }
        val cell = Cell(element)
        return array[index].compareAndSet(prev, cell)
    }

    fun tryLock(index: Int): Boolean {
        return if (array[index].value != null) {
            array[index].value!!.moved.compareAndSet(expect = false, update = true)
        } else {
            false
        }
    }

    fun tryPlace(index: Int, element: E): Boolean {
        var newIndex = index
        while (true) {
            val prev = array[newIndex].value
            if (prev != null) {
                newIndex += 1
            } else {
                break
            }
        }
        val prev = array[newIndex].value
        val cell = Cell(element)
        if (array[newIndex].compareAndSet(prev, cell)) {
            if (newIndex >= _size.value) {
                val size = _size.value
                if (_size.compareAndSet(size, newIndex + 1)) {
                    return true
                } else {
                    while (true) {
                        var newSize = 0
                        for (i in 0 until array.size) {
                            val tmpCell = array[i].value
                            if (tmpCell != null) {
                                if (tmpCell.value.value != null) {
                                    newSize = i + 1
                                }
                            }
                        }
                        if (_size.compareAndSet(size, newSize)) {
                            return true
                        }
                    }
                }
            }
            return true
        } else {
            return false
        }
    }

    fun getSize(): Int {
        return _size.value
    }

    fun trySetSize(expected: Int, new: Int): Boolean {
        return _size.compareAndSet(expected, new)
    }
}

private class Cell<E>(element: E? = null) {
    val value = atomic<E?>(element)
    val moved = atomic<Boolean>(false)

    fun getValue(): E? {
        return value.value
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME