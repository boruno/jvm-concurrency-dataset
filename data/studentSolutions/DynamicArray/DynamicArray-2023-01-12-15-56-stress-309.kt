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
        var prevCore = core.value
        while (selectedCore.next.value != null) {
            prevCore = selectedCore
            selectedCore = selectedCore.next.value!!
        }
        if (selectedCore.migrationStatus.value) {
            selectedCore = prevCore
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
        while (true) {
            print("Push ")
            println(element)
            var selectedCore = core.value
            var prevCore = core.value
            while (selectedCore.next.value != null) {
                prevCore = selectedCore
                selectedCore = selectedCore.next.value!!
            }
            if (selectedCore.migrationStatus.value) {
                selectedCore = prevCore
            }
            // Пробуем создать новый массив
            selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))

            // Пробуем перенести элементы в новый массив
            for (i in 0 until selectedCore.getSize()) {
                selectedCore.tryLock(i)
                //if (selectedCore.tryLock(i)) {
                selectedCore.next.value?.tryPlace(i, selectedCore.get(i))
                //}
            }

            // Пробуем поместить элемент в новый массив
            if (selectedCore.next.value!!.tryPlace(selectedCore.getSize(), element)) {
                // Пробуем завершить перенос
                selectedCore.next.value!!.migrationStatus.compareAndSet(expect = true, update = false)
                return
            } else {
                selectedCore.next.value!!.tryUpdateSize()
                selectedCore.next.value!!.migrationStatus.compareAndSet(expect = true, update = false)
            }
        }


//        if (!selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))) {
//            if (!selectedCore.next.value!!.migrationStatus.value) {
//                while (selectedCore.next.value != null) {
//                    prevCore = selectedCore
//                    selectedCore = selectedCore.next.value!!
//                }
//                selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))
//            }
//        }
//
//        for (i in 0 until selectedCore.getSize()) {
//            if (selectedCore.tryLock(i)) {
//                selectedCore.next.value?.tryPlace(i, selectedCore.get(i))
//            }
//        }
//
//        if (!selectedCore.next.value!!.tryPlace(selectedCore.getSize(), element)) {
//            selectedCore.next.value!!.tryUpdateSize()
////            println("Last place fail")
////            println(selectedCore.getSize())
//            while (true) {
//                selectedCore = selectedCore.next.value!!
//                selectedCore.next.compareAndSet(null, Core(selectedCore.maxSize * 2, true, selectedCore.getSize()))
//                for (i in 0 until selectedCore.getSize()) {
//                    if (selectedCore.tryLock(i)) {
//                        selectedCore.next.value?.tryPlace(i, selectedCore.get(i))
//                    }
//                }
//                if (selectedCore.next.value!!.tryPlace(selectedCore.next.value!!.getSize(), element)) {
//                    break
//                }
//            }
//        }
//        selectedCore.next.value!!.migrationStatus.compareAndSet(expect = true, update = false)
    }

    override val size: Int
        get() {
            var selectedCore = core.value
            while (selectedCore.next.value != null) {
                selectedCore = selectedCore.next.value!!
            }
            return selectedCore.getSize()
        }
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
        val size = _size.value
        val prev = array[index].value
        if (prev != null) {
            //println("???")
            return false
        }
        val cell = Cell(element)
        if (array[index].compareAndSet(prev, cell)) {
            if (index >= size) {
                if (_size.compareAndSet(size, index + 1)) {
                    return true
                } else {
                    if (index >= _size.value) {
                        return false
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

    fun tryUpdateSize(): Boolean {
        val oldSize = _size.value
        var newSize = 0
        for (i in 0 until array.size) {
            val tmpCell = array[i].value
            if (tmpCell != null) {
                if (tmpCell.value.value != null) {
                    newSize = i + 1
                }
            }
        }
        return _size.compareAndSet(oldSize, newSize)
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