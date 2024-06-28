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

//    val capacity: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val newCore: AtomicRef<Core<E>?> = atomic(null)
    private val migrationStatus = atomic<Boolean>(false)

    override fun get(index: Int): E {
        var selectedCore = core.value
        while (selectedCore.next.value != null) {
            selectedCore = selectedCore.next.value!!
        }
        return selectedCore.get(index)
    }

    override fun put(index: Int, element: E) {
        var selectedCore = core.value
        while (selectedCore.next.value != null) {
            selectedCore = selectedCore.next.value!!
        }
        require(index < selectedCore.getSize())

        if (!selectedCore.tryPut(index, element)) {
            while (true) {
                if (!selectedCore.migrationStatus.value) {
                    selectedCore.tryPut(index, element)
                    return
                }
            }
        }

    }

    override fun pushBack(element: E) {
        var selectedCore = core.value
        var prevCore = core.value
        while (selectedCore.next.value != null) {
            prevCore = selectedCore
            selectedCore = selectedCore.next.value!!
        }
        if (selectedCore.migrationStatus.value) {
            selectedCore = prevCore
        }
//        if (selectedCore.migrationStatus.value) {
//            for (i in 0 until prevCore.getSize()) {
//                if (prevCore.tryLock(i)) {
//                    selectedCore.next.value?.tryPlace(i, get(i))
//                }
//            }
//            while (true) {
//                if (!selectedCore.migrationStatus.value) {
//                    break
//                }
//            }
//        }
        selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))
//        var oldCore = core.value
//        migrationStatus.compareAndSet(expect = false, update = true)
//        newCore.compareAndSet(null, Core(oldCore.maxSize * 2))
        //selectedCore.next.value!!.trySetSize(0, selectedCore.getSize())

        for (i in 0 until selectedCore.getSize()) {
            if (selectedCore.tryLock(i)) {
                selectedCore.next.value?.tryPlace(i, selectedCore.get(i))
            }
        }

        if (!selectedCore.next.value!!.tryPlace(selectedCore.next.value!!.getSize(), element)) {
            println("Last place fail")
            while (true) {
                selectedCore = selectedCore.next.value!!
                selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))
                for (i in 0 until selectedCore.getSize()) {
                    if (selectedCore.tryLock(i)) {
                        selectedCore.next.value?.tryPlace(i, selectedCore.get(i))
                    }
                }
                if (selectedCore.next.value!!.tryPlace(selectedCore.next.value!!.getSize(), element)) {
                    break
                }
            }
        }

//        if (selectedCore.value.next.value?.tryPlace(selectedCore.value.getSize(), element) == false) {
//
//        }

//        if (newCore.value?.tryPlace(oldCore.getSize(), element) == true) {
//
//        } else {
//            while (true) {
//                if (!migrationStatus.value || newCore.value == null) {
//                    val place = core.value.getSize()
//                    if (core.value.tryPlace(place, element)) {
//                        break
//                    }
//                } else {
//                    val place = newCore.value?.getSize() ?: continue
//                    if (newCore.value?.tryPlace(place, element) == true) {
//                        break
//                    }
//                }
//            }
//        }
//        oldCore = core.value
//        newCore.value?.let { core.compareAndSet(oldCore, it) }
//        newCore.compareAndSet(newCore.value, null)
        selectedCore.next.value!!.migrationStatus.compareAndSet(expect = true, update = false)
    }

    override val size: Int
        get() {
            var selectedCore = core.value
            while (selectedCore.next.value != null) {
                selectedCore = selectedCore.next.value!!
            }
            return selectedCore.getSize()
        }

//    override val capacity: Int get() = core.value.maxSize
}

private class Core<E>(
    capacity: Int,
    status: Boolean = false,
    size: Int = 0
) {
    private val array = atomicArrayOfNulls<Cell<E>>(capacity)
    private val _size = atomic(size)
    val migrationStatus = atomic<Boolean>(status)
    val maxSize: Int = capacity


    val next: AtomicRef<Core<E>?> = atomic(null)

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
//        while (true) {
//            val prev = array[newIndex].value
//            if (prev != null) {
//                newIndex += 1
//            } else {
//                break
//            }
//        }
        val size = _size.value
        val prev = array[newIndex].value
        if (prev != null) {
            return false
        }
        val cell = Cell(element)
        if (array[newIndex].compareAndSet(prev, cell)) {
            if (newIndex >= size) {
                return _size.compareAndSet(size, newIndex + 1)
            }
            return true
        } else {
            return false
        }
//                val size = _size.value
//                if (_size.compareAndSet(size, newIndex + 1)) {
//                    return true
//                } else {
//                    while (true) {
//                        var newSize = 0
//                        for (i in 0 until array.size) {
//                            val tmpCell = array[i].value
//                            if (tmpCell != null) {
//                                if (tmpCell.value.value != null) {
//                                    newSize = i + 1
//                                }
//                            }
//                        }
//                        if (_size.compareAndSet(size, newSize)) {
//                            return true
//                        }
//                    }
//                }

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