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

interface Movable<T> {
    val value: T
}
class Moved<T>(override val value: T): Movable<T>
class NotMoved<T>(override val value: T): Movable<T>

class DynamicArrayImpl<E> : DynamicArray<E> {
    companion object {
        private const val EXTENSION_RATE = 2
    }

    private val curCore = atomic(Core<E>(INITIAL_CAPACITY))
    private val nextCore: AtomicRef<Core<E>?> = atomic(null)

    override fun get(index: Int): E {
        require(index < size)
        return curCore.value.array[index].value!!.value
//        while (true) {
//            val e = curCore.value.array[index].value
//            if (e is NotMoved) {
//                return e.value
//            } else {
//                move()
//            }
//        }
//        curCore.value.get(index)
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val e = curCore.value.array[index].value
            if (e is NotMoved) {
                if (curCore.value.array[index].compareAndSet(e, NotMoved(element))) {
                    return
                }
            } else {
                move()
            }
        }
//        if (curCore.value != nextCore.value) {
//            nextCore.value.put(index, element)
//        } else {
//            curCore.value.put(index, element)
//        }
    }

    private fun move() {
        val curCore = this.curCore.value
        val core = Core<E>(curCore.capacity * 2)
        core.size.value = curCore.capacity
        nextCore.compareAndSet(null, core)
        for (i in 0 until curCore.capacity) {
            var v: Movable<E>?
            do {
                v = curCore.array[i].value
            } while (v is NotMoved<*> && !curCore.array[i].compareAndSet(v, Moved(v.value)))
            v!!
            nextCore.value!!.array[i].compareAndSet(null, NotMoved(v.value))
        }
        if (nextCore.compareAndSet(core, null)) {
            this.curCore.compareAndSet(curCore, core)
        }
    }

    override fun pushBack(element: E) {
        while(true) {
            val s = size
            if (s >= curCore.value.capacity) {
                move()
            } else {
                if (curCore.value.array[s].compareAndSet(null, NotMoved(element))) {
                    curCore.value.size.getAndIncrement()
                    return
                }
            }
        }
//        println("TETS")
//        if (curCore.value != nextCore.value) {
//            nextCore.value.pushBack(element)
//            return
//        }
//        if (size >= curCore.value.capacity) {
//            val next = Core<E>(size * EXTENSION_RATE)
//            next.setSize(size + 1)
//            if (nextCore.compareAndSet(curCore.value, next)) {
//                for (i in 0 until size) {
//                    nextCore.value.put(i, curCore.value.get(i))
//                }
//                nextCore.value.put(size, element)
//                curCore.value = nextCore.value
//            } else {
//                nextCore.value.pushBack(element)
//            }
//        } else {
//            var oldSize: Int
//            do {
//                oldSize = size
//                if (oldSize >= curCore.value.capacity) {
//                    pushBack(element)
//                }
//            } while (!curCore.value._size.compareAndSet(oldSize, oldSize + 1))
//            curCore.value.put(oldSize, element)
////            if (!curCore.value.array[size].compareAndSet(null, element)) {
////                pushBack(element)
////            }
////            curCore.value._size.getAndIncrement()
//        }
    }

    override val size: Int get() = curCore.value.size.value
}

private class Core<T>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Movable<T>?>(capacity)
    val size = atomic(0)

//    val size: Int = _size.value
//
//    fun setSize(size: Int) {
//        _size.value = size
//    }

//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < size) { "Index $index out of bounds of array with capacity $capacity" }
//        return array[index].value as E
//    }
//
//    fun put(index: Int, element: E) {
//        array[index].value = element
//    }
//
//    @Suppress("ControlFlowWithEmptyBody")
//    fun pushBack(element: E) {
//        require(size < capacity) { "Not enough space in internal array with capacity $capacity" }
//        while (!array[size].compareAndSet(null, element)) {}
//        _size.getAndIncrement()
//    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME