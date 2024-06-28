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
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        var curCore = core.value
        while (true) {
            val curCell = curCore.array[index].value
            val status = curCell?.status
            if (status == Status.NORMAL) {
                if (curCore.array[index].compareAndSet(curCell, Cell(element))) {
                    return
                }
            } else if (status == Status.MOVED || status == Status.FIXED) {
                transfer(index)
                curCore.nextCore.value?.let { core.compareAndSet(curCore, it) }
                curCore = core.value
            }
        }
    }

    private fun transfer(index: Int) {
        val curCore = core.value
        val next = curCore.nextCore.value
        if (next == null) {
            return
        }
        var indexCounter = index
        while (indexCounter < curCore.getCapacity()) {
            val curCell = curCore.array[indexCounter].value
            val status = curCell?.status
            if (status == Status.NORMAL) {
                val fixed = curCell.createFixed()
                if (curCore.array[indexCounter].compareAndSet(curCell, fixed)) {
                    next.array[indexCounter].compareAndSet(null, curCell)
                    curCore.array[indexCounter].compareAndSet(fixed, curCell.createMoved())
                    indexCounter++
                }
            } else if (status == Status.FIXED) {
                next.array[indexCounter].compareAndSet(null, curCell.createNormal())
                curCore.array[indexCounter].compareAndSet(curCell, curCell.createMoved())
                indexCounter++
            } else {
                indexCounter++
            }
        }
    }

    override fun pushBack(element: E) {
        var curCore = core.value
        while (true) {
            val curSize = size
            val curCapacity = curCore.getCapacity()
            if (curSize < curCapacity) {
                if (curCore.array[curSize].compareAndSet(null, Cell(element))) {
                    curCore.incSize()
                    return
                }
            } else {
                val next = curCore.nextCore
                if(next.compareAndSet(null, Core(2 * curCapacity))) {
                    transfer(0)
                    next.value?.let { core.compareAndSet(curCore, it) }
                }
                curCore = core.value
            }
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(
    capacity: Int,
) {
    public val array = atomicArrayOfNulls<Cell<E>>(capacity)
    private val _size = atomic(0)
    public val nextCore: AtomicRef<Core<E>?> = atomic(null);

    val size: Int = _size.value

    fun getCapacity(): Int {
        return array.size
    }

    fun incSize() {
        _size.incrementAndGet()
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value?.element as E
    }


}

private enum class Status {
    NORMAL, FIXED, MOVED
}

private class Cell<E> (var element: E) {
    var status: Status = Status.NORMAL

    public fun createNormal(): Cell<E> {
        return Cell(element)
    }
    public fun createFixed(): Cell<E> {
        val fixed = Cell(element)
        fixed.status = Status.FIXED
        return fixed
    }

    public fun createMoved(): Cell<E> {
        val moved = Cell(element)
        moved.status = Status.MOVED
        return moved
    }


}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME





//package mpp.dynamicarray
//
//import kotlinx.atomicfu.*
//
//interface DynamicArray<E> {
//    /**
//     * Returns the element located in the cell [index],
//     * or throws [IllegalArgumentException] if [index]
//     * exceeds the [size] of this array.
//     */
//    fun get(index: Int): E
//
//    /**
//     * Puts the specified [element] into the cell [index],
//     * or throws [IllegalArgumentException] if [index]
//     * exceeds the [size] of this array.
//     */
//    fun put(index: Int, element: E)
//
//    /**
//     * Adds the specified [element] to this array
//     * increasing its [size].
//     */
//    fun pushBack(element: E)
//
//    /**
//     * Returns the current size of this array,
//     * it increases with [pushBack] invocations.
//     */
//    val size: Int
//}
//
//class DynamicArrayImpl<E> : DynamicArray<E> {
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
//
//    override fun get(index: Int): E = core.value.get(index)
//
//    override fun put(index: Int, element: E) {
//        require(index < size)
//        var curCore = core.value
//        while (true) {
//            val curCell = curCore.getCell(index).value
//            val status = curCell?.status
//            if (status == Status.NORMAL) {
//                if (curCore.getCell(index).compareAndSet(curCell, Cell(element))) {
//                    return
//                }
//            } else if (status == Status.MOVED || status == Status.FIXED) {
//                transfer(index)
//                curCore.getNext().value?.let { core.compareAndSet(curCore, it) }
//                curCore = core.value
//            }
//        }
//    }
//
//    private fun transfer(index: Int) {
//        val curCore = core.value
//        val next = curCore.getNext().value
//        if (next == null) {
//            return
//        }
//        var indexCounter = index
//        while (indexCounter < curCore.getCapacity()) {
//            val curCell = curCore.getCell(indexCounter).value
//            val status = curCell?.status
//            if (status == Status.NORMAL) {
//                val fixed = curCell.createFixed()
//                if (curCore.getCell(indexCounter).compareAndSet(curCell, fixed)) {
//                    next.getCell(indexCounter).compareAndSet(null, curCell)
//                    curCore.getCell(indexCounter).compareAndSet(fixed, curCell.createMoved())
//                    indexCounter++
//                }
//            } else if (status == Status.FIXED) {
//                next.getCell(indexCounter).compareAndSet(null, curCell.createNormal())
//                curCore.getCell(indexCounter).compareAndSet(curCell, curCell.createMoved())
//                indexCounter++
//            } else {
//                indexCounter++
//            }
//        }
//    }
//
//    override fun pushBack(element: E) {
//        var curCore = core.value
//        while (true) {
//            val curSize = size
//            val curCapacity = curCore.getCapacity()
//            if (curSize < curCapacity) {
//                if (curCore.getCell(curSize).compareAndSet(null, Cell(element))) {
//                    curCore.incSize()
//                    return
//                }
//            } else {
//                val next = curCore.getNext()
//                if(next.compareAndSet(null, Core(2 * curCapacity))) {
//                    transfer(0)
//                    next.value?.let { core.compareAndSet(curCore, it) }
//                }
//                curCore = core.value
//            }
//        }
//    }
//
//    override val size: Int get() = core.value.size
//}
//
//private class Core<E>(
//    capacity: Int,
//) {
//    private val array = atomicArrayOfNulls<Cell<E>>(capacity)
//    private val _size = atomic(0)
//    private val nextCore: AtomicRef<Core<E>?> = atomic(null);
//
//    val size: Int = _size.value
//
//    fun getCell(index: Int): AtomicRef<Cell<E>?> {
//        return array[index]
//    }
//
//    fun getNext(): AtomicRef<Core<E>?> {
//        return nextCore
//    }
//
//    fun getCapacity(): Int {
//        return array.size
//    }
//
//    fun incSize() {
//        _size.incrementAndGet()
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < size)
//        return array[index].value?.element as E
//    }
//}
//
//private enum class Status {
//    NORMAL, FIXED, MOVED
//}
//
//private class Cell<E> (var element: E) {
//    var status: Status = Status.NORMAL
//
//    public fun createNormal(): Cell<E> {
//        return Cell(element)
//    }
//    public fun createFixed(): Cell<E> {
//        val fixed = Cell(element)
//        fixed.status = Status.FIXED
//        return fixed
//    }
//
//    public fun createMoved(): Cell<E> {
//        val moved = Cell(element)
//        moved.status = Status.MOVED
//        return moved
//    }
//
//
//}
//
//private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME