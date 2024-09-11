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

//class DynamicArrayImpl<E> : DynamicArray<E> {
//    private val core = atomic(Core<E>(INITIAL_CAPACITY, 0))
//
//    override fun get(index: Int): E = core.value.get(index)
//
//    override fun put(index: Int, element: E) = core.value.put(index, element)
//
//    override fun pushBack(element: E) {
//        while (true) {
//            val curCore = core.value
//            if (curCore.pushBack(element)) return
//
//            core.compareAndSet(curCore, curCore.move())
//        }
//    }
//
//    override val size: Int get() = core.value._size.value
//}
//
//class Core<E>(
//    val capacity: Int, curSize: Int
//) {
//    private val array = atomicArrayOfNulls<Element<E>>(capacity)
//    private val next: AtomicRef<Core<E>?> = atomic(this)
//    val _size = atomic(0)
//
//    val size: Int = _size.value
//
//    fun get(index: Int): E {
//        checkBounds(index)
//
//        while (true) {
//            val elementAtIndex = array[index].value!!
//
//            when (elementAtIndex.status) {
//                STATUS.REAL -> {
//                    return elementAtIndex.element
//                }
//                STATUS.FIX_VALUE -> {
//                    return elementAtIndex.element
//                }
//                STATUS.MOVED -> {
//                    val curCoreNext = next.value
//                    if (curCoreNext != null) {
//                        return curCoreNext.get(index)
//                    } else {
//                        continue
//                    }
//                }
//            }
//        }
//    }
//
//    fun put(index: Int, element: E) {
//        checkBounds(index)
//
//        val elementToPut = Element(element, STATUS.REAL)
//
//        while (true) {
//            val curElementAtIndex = array[index].value!!
//
//
//            when (curElementAtIndex.status) {
//                STATUS.REAL -> {
//                    if (array[index].compareAndSet(curElementAtIndex, elementToPut)) {
//                        break
//                    }
//                }
//                STATUS.FIX_VALUE -> {
//                    val curCoreNext = next.value!!
//                    curCoreNext.array[index].compareAndSet(
//                        null,
//                        Element(curElementAtIndex.element, STATUS.REAL)
//                    )
//                    array[index].compareAndSet(
//                        curElementAtIndex,
//                        Element(curElementAtIndex.element, STATUS.MOVED)
//                    )
//                    continue
//                }
//                STATUS.MOVED -> {
//                    val curCoreNext = next.value
//                    if (curCoreNext != null) {
//                        curCoreNext.put(index, element)
//                        break
//                    } else {
//                        continue
//                    }
//                }
//            }
//        }
//    }
//
//    fun pushBack(element: E): Boolean {
//        val elementToPush = Element(element, STATUS.REAL)
//
//        while (true) {
//            val idx = _size.value
//
//            if (idx < capacity) {
//                if (array[idx].compareAndSet(null, elementToPush)) {
//                    _size.compareAndSet(idx, idx + 1)
//                    return true
//                }
//                _size.compareAndSet(idx, idx + 1)
//            } else {
//                return false
//            }
//        }
//    }
//
//    fun move(): Core<E> {
//        if (next.value == this) next.compareAndSet(this, Core(array.size * 2, size))
//
//        for(index in 0 until size) {
//            while (true) {
//                val oldValue = array[index].value!!
//                when (oldValue.status) {
//                    STATUS.REAL -> {
//                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.FIX_VALUE))
//                    }
//                    STATUS.FIX_VALUE -> {
//                        next.value!!.array[index].compareAndSet(null, Element(oldValue.element, STATUS.REAL))
//                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.MOVED))
//                        continue
//                    }
//                    else -> {
//                        break
//                    }
//                }
//            }
//        }
//        return next.value!!
//    }
//
//    private fun getResizedArray(): Core<E>? {
//        val newCore = Core<E>(capacity * 2, capacity)
//
//        next.compareAndSet(null, newCore)
//
//        for (i in 0 until capacity) {
//            while (true) {
//                val elementInPrevCore = array[i].value ?: break
//                val curCoreNext = next.value!!
//
//                when (elementInPrevCore.status) {
//                    STATUS.REAL -> {
//                        array[i].compareAndSet(
//                            elementInPrevCore,
//                            Element(elementInPrevCore.element, STATUS.FIX_VALUE)
//                        )
//                    }
//
//                    STATUS.FIX_VALUE -> {
//                        curCoreNext.array[i].compareAndSet(
//                            null,
//                            Element(elementInPrevCore.element, STATUS.REAL)
//                        )
//                        array[i].compareAndSet(
//                            elementInPrevCore,
//                            Element(elementInPrevCore.element, STATUS.MOVED)
//                        )
//                        continue
//                    }
//
//                    STATUS.MOVED -> {
//                        break
//                    }
//                }
//            }
//        }
//
//        return next.value
//    }
//
//    private fun checkBounds(index: Int) {
//        if (index < 0 || index >= size) {
//            throw IllegalArgumentException("Index out of bound: index=${index}, size=${size}")
//        }
//    }
//}

class Element<E>(val element: E, val status: STATUS)
enum class STATUS {
    MOVED, FIX_VALUE, REAL
}


class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        return core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return

            core.compareAndSet(curCore, curCore.move())
        }
    }

    override val size: Int get() {
        return core.value.size
    }
}

private class Core<E>(capacity: Int, curSize: Int) {
    private val array: AtomicArray<Element<E>?> = atomicArrayOfNulls(capacity)
    private val next: AtomicRef<Core<E>> = atomic(this)
    private val size_: AtomicInt = atomic(curSize)

    fun get(index: Int): E {
        if (index in 0 until size) {
            val elementAtCurIndex = array[index].value!!
            val curCoreNext = next.value

            when (elementAtCurIndex.status) {
                STATUS.REAL -> {
                    return elementAtCurIndex.element
                }
                STATUS.FIX_VALUE -> {
                    return elementAtCurIndex.element
                }
                STATUS.MOVED -> {
                    return curCoreNext.get(index)
                }
            }
        } else {
            throw IllegalArgumentException("Index out of bound: index=$index, size=$size")
        }
    }

    fun put(index: Int, value: E) {
        if (index in 0 until size) {
            while (true) {
                val elementAtCurIndex = array[index].value!!
                val curCoreNext = next.value

                when (elementAtCurIndex.status) {
                    STATUS.REAL -> {
                        if (array[index].compareAndSet(elementAtCurIndex, Element(value, STATUS.REAL))) {
                            return
                        }
                    }
                    STATUS.FIX_VALUE -> {
                        if (curCoreNext.array[index].compareAndSet(null, Element(elementAtCurIndex.element, STATUS.REAL))) {
                            array[index].compareAndSet(elementAtCurIndex, Element(elementAtCurIndex.element, STATUS.MOVED))
                        }
                        continue
                    }

                    else -> {
                        curCoreNext.put(index, value)
                        break
                    }
                }
            }
        } else {
            throw IllegalArgumentException("Index out of bound: index=$index, size=$size")
        }
    }

    fun pushBack(value: E) : Boolean {
        while (true) {
            val oldSize = size
            if (oldSize == array.size) return false

            if (array[oldSize].compareAndSet(null, Element(value, STATUS.REAL))) {
                size_.compareAndSet(oldSize, oldSize + 1)
                return true
            }
            size_.compareAndSet(oldSize, oldSize + 1)
        }
    }

    val size: Int get() {
        return size_.value
    }

    fun move(): Core<E> {
        if (next.value == this) next.compareAndSet(this, Core(array.size * 2, size))

        for(index in 0 until size) {
            while (true) {
                val oldValue = array[index].value!!

                when (oldValue.status) {
                    STATUS.REAL -> {
                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.FIX_VALUE))
                    }
                    STATUS.FIX_VALUE -> {
                        next.value.array[index].compareAndSet(null, Element(oldValue.element, STATUS.REAL))
                        array[index].compareAndSet(oldValue, Element(oldValue.element, STATUS.MOVED))
                        continue
                    }
                    else -> {
                        break
                    }
                }
            }
        }
        return next.value
    }
}

private interface Node<E>

private class Common<E>(val value: E): Node<E>
private class Moving<E>(val value: E): Node<E>
private class Moved<E>: Node<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME