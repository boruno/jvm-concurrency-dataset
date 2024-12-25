//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

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



class DynamicArrayImpl<T> : DynamicArray<T> {
    private val core: AtomicRef<Core<T>> = atomic(Core(INITIAL_CAPACITY))

    override fun get(index: Int): T {
        val curCore = core.value
        if (index >= curCore.getSize()) {
            throw IllegalArgumentException()
        }
        val x = curCore.array[index].value
        when {
            x != null -> return x.element
            else -> throw RuntimeException("unexpected case")
        }
    }

    override fun put(index: Int, element: T) {
        var curCore = core.value
        if (index >= curCore.getSize()) {
            throw IllegalArgumentException()
        }
        curCore.array[index].getAndSet(Cell(element))
        while (true) {
            val nextCore = curCore.nextCore.value
            when {
                nextCore != null -> {
                    val y = curCore.array[index].value
                    if (y != null) {
                        nextCore.array[index].getAndSet(y)
                    }
                    curCore = nextCore
                }
                else -> return
            }
        }
    }

    override fun pushBack(element: T) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.getSize()
            if (curSize < curCore.getCapacity()) {
                if (curCore.array[curSize].compareAndSet(null, Cell(element))) {
                    curCore.size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    curCore.size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                val nextCore = Core<T>(2 * curCore.getCapacity())
                if (curCore.nextCore.compareAndSet(null, nextCore)) {
                    make(curCore, nextCore)
                } else {
                    val nextNode = curCore.nextCore.value
                    if (nextNode != null) {
                        make(curCore, nextNode)
                    }
                }
            }
        }
    }

    private fun make(curCore: Core<T>, nextCore: Core<T>) {
        for (i in 1..curCore.getCapacity()) {
            val y = curCore.array[i - 1].value
            if (y != null) {
                nextCore.array[i - 1].compareAndSet(null, y)
                nextCore.size.incrementAndGet()
            }
        }
        core.compareAndSet(curCore, nextCore)
    }

    override val size: Int
        get() {
            return core.value.size.value
        }
}

private class Core<E>(
    capacity: Int,
) {
    public val array = atomicArrayOfNulls<Cell<E>>(capacity)
    public val size = atomic(0)
    public val nextCore: AtomicRef<Core<E>?> = atomic(null);

    fun getCapacity(): Int {
        return array.size
    }

    fun getSize(): Int {
        return size.value
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size.value)
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


//class DynamicArrayImpl<E> : DynamicArray<E> {
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
//    private val sz = atomic(0)
//
//    private fun check(index: Int) {
//        if (index >= size) {
//            throw IllegalArgumentException("Index ($index) is illegal")
//        }
//    }
//
//    override fun get(index: Int): E {
//        check(index)
//        return core.value.array[index].value!!.value
//    }
//
//    override fun put(index: Int, element: E) {
//        check(index)
//        var curCore = core.value
//
//        while (true) {
//            when (val cur = curCore.array[index].value!!) {
//                is Basic -> {
//                    if (curCore.array[index].compareAndSet(cur, Basic(element))) {
//                        return
//                    }
//                }
//                is Fixed, is Moved -> {
//                    move()
//                    val curNext = curCore.next.value!!
//                    core.compareAndSet(curCore, curNext)
//                    curCore = core.value
//                }
//            }
//        }
//    }
//
//        override fun pushBack(element: E) {
//        while (true) {
//            val curHead = core.value
//            val curSize = size
//            if (curSize < curHead.capacity) {
//                if (curHead.array[curSize].compareAndSet(null, Basic(element))) {
//                    sz.compareAndSet(curSize, curSize + 1)
//                    return
//                } else {
//                    sz.compareAndSet(curSize, curSize + 1)
//                }
//            } else {
//                val newNode = Core<E>(2 * core.value.capacity)
//                if (curHead.next.compareAndSet(null, newNode)) {
//                    move()
//                } else {
//                    val nextNode = curHead.next.value
//                    if (nextNode != null) {
//                        move()
//                    }
//                }
//            }
//        }
//    }
//
//
////    override fun pushBack(element: E) {
////        var curCore = core.value
////
////        while (true) {
////            val curSize = size
////            val curCapacity = curCore.capacity
////
////            if (curSize < curCapacity) {
////                if (curCore.array[curSize].compareAndSet(null, Basic(element))) {
////                    sz.incrementAndGet()
////                    return
////                }
////            } else {
////                if (curCore.next.compareAndSet(null, Core(2 * curCapacity))) {
////                    move()
////                    core.compareAndSet(curCore, curCore.next.value!!)
////                } else {
////                    val nextNode = curCore.next.value
////                    if (nextNode != null) {
////                        move()
////                    }
////                }
////                curCore = core.value
////            }
////        }
////    }
//
//    override val size: Int
//        get() {
//            return sz.value
//        }
//
//    private fun move() {
//        val curCore = core.value
//        val nextCore = curCore.next.value ?: return
//
//        var ind = 0
//        while (ind < curCore.capacity) {
//            when (val current = curCore.array[ind].value!!) {
//                is Moved -> ++ind
//                is Basic -> {
//                    val fixedVal = Fixed(current)
//                    if (curCore.array[ind].compareAndSet(current, fixedVal)) {
//                        nextCore.array[ind].compareAndSet(null, current)
//                        curCore.array[ind++].compareAndSet(fixedVal, Moved(current))
//                    }
//                }
//                is Fixed -> {
//                    nextCore.array[ind].compareAndSet(null, Basic(current))
//                    curCore.array[ind++].compareAndSet(current, Moved(current))
//                }
//            }
//        }
//    }
//}
//
//private class Core<E>(val capacity: Int) {
//    val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
//    val next = atomic<Core<E>?>(null)
//}
//
//private open class Wrapper<E>(val value: E)
//
//private class Fixed<E>(e: Wrapper<E>) : Wrapper<E>(e.value)
//
//private class Moved<E>(e: Wrapper<E>) : Wrapper<E>(e.value)
//
//private class Basic<E>(e: Wrapper<E>) : Wrapper<E>(e.value) {
//    constructor(value: E) : this(Wrapper(value))
//}
//
//private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME










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
//            val curCell = curCore.array[index].value
//            val status = curCell?.status
//            if (status == Status.NORMAL) {
//                if (curCore.array[index].compareAndSet(curCell, Cell(element))) {
//                    return
//                }
//            } else if (status == Status.MOVED || status == Status.FIXED) {
//                transfer()
//                curCore.nextCore.value?.let { core.compareAndSet(curCore, it) }
//                curCore = core.value
//            }
//        }
//    }
//
//    private fun transfer() {
//        val curCore = core.value
//        val next = curCore.nextCore.value
//        if (next == null) {
//            return
//        }
//        var index = 0
//        while (index < curCore.getCapacity()) {
//            val curCell = curCore.array[index].value
//            val status = curCell?.status
//            if (status == Status.NORMAL) {
//                val fixed = curCell.createFixed()
//                if (curCore.array[index].compareAndSet(curCell, fixed)) {
//                    next.array[index].compareAndSet(null, curCell)
//                    next.incSize()
//                    curCore.array[index].compareAndSet(fixed, curCell.createMoved())
//                    index++
//                }
//            } else if (status == Status.FIXED) {
//                next.array[index].compareAndSet(null, curCell.createNormal())
//                next.incSize()
//                curCore.array[index].compareAndSet(curCell, curCell.createMoved())
//                index++
//            } else {
//                index++
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
//                if (curCore.array[curSize].compareAndSet(null, Cell(element))) {
//                    curCore.incSize()
//                    break
//                }
//            } else {
//                val next = curCore.nextCore
//                if (next.compareAndSet(null, Core(2 * curCapacity))) {
//                    transfer()
//                    next.value?.let { core.compareAndSet(curCore, it) }
//                }
//                curCore = core.value
//            }
//        }
//    }
//
//    override val size: Int get() = core.value.getSize()
//}
//
//private class Core<E>(
//    capacity: Int,
//) {
//    public val array = atomicArrayOfNulls<Cell<E>>(capacity)
//    private val size = atomic(0)
//    public val nextCore: AtomicRef<Core<E>?> = atomic(null);
//
//    fun getCapacity(): Int {
//        return array.size
//    }
//
//    fun incSize() {
//        size.incrementAndGet()
//    }
//
//    fun getSize(): Int {
//        return size.value
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < size.value)
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