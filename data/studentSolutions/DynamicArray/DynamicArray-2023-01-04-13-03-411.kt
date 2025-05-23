


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
    private val sz = atomic(0)

    private fun check(index: Int) {
        if (index >= size) {
            throw IllegalArgumentException("Index ($index) is illegal")
        }
    }

    override fun get(index: Int): E {
        check(index)
        return core.value.array[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        check(index)
        var curCore = core.value

        while (true) {
            when (val cur = curCore.array[index].value!!) {
                is Basic -> {
                    if (curCore.array[index].compareAndSet(cur, Basic(element))) {
                        return
                    }
                }
                is Fixed, is Moved -> {
                    move(index)
                    val curNext = curCore.next.value!!
                    core.compareAndSet(curCore, curNext)
                    curCore = core.value
                }
            }
        }
    }

    override fun pushBack(element: E) {
        var curCore = core.value

        while (true) {
            val curSize = size
            val curCapacity = curCore.capacity

            if (curSize < curCapacity) {
                if (curCore.array[curSize].compareAndSet(null, Basic(element))) {
                    sz.incrementAndGet()
                    return
                }
            } else {
                if(curCore.next.compareAndSet(null, Core(2 * curCapacity))) {
                    move(0)
                    core.compareAndSet(curCore, curCore.next.value!!)
                }
                curCore = core.value
            }
        }
    }

    override val size: Int
        get() {
            return sz.value
        }

    private fun move(index: Int) {
        val curCore = core.value
        val nextCore = curCore.next.value ?: return

        var ind = index
        while (ind < curCore.capacity) {
            when (val current = curCore.array[ind].value!!) {
                is Moved -> ++ind
                is Basic -> {
                    val fixedVal = Fixed(current)
                    if (curCore.array[ind].compareAndSet(current, fixedVal)) {
                        nextCore.array[ind].compareAndSet(null, current)
                        curCore.array[ind++].compareAndSet(fixedVal, Moved(current))
                    }
                }
                is Fixed -> {
                    nextCore.array[ind].compareAndSet(null, Basic(current))
                    curCore.array[ind++].compareAndSet(current, Moved(current))
                }
            }
        }
    }
}

private class Core<E>(val capacity: Int) {
    val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
    val next = atomic<Core<E>?>(null)
}

private open class Wrapper<E>(val value: E)

private class Fixed<E>(e: Wrapper<E>) : Wrapper<E>(e.value)

private class Moved<E>(e: Wrapper<E>) : Wrapper<E>(e.value)

private class Basic<E>(e: Wrapper<E>) : Wrapper<E>(e.value) {
    constructor(value: E) : this(Wrapper(value))
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
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