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

//@Suppress("UNCHECKED_CAST")
//class DynamicArrayImpl<E> : DynamicArray<E> {
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
//
//    override fun get(index: Int): E {
//        require(index < size)
//
//        while (true) {
//            if (index >= core.value.array.size) continue
//            val value = core.value.array[index].value
//
//            if (value != null) {
//                return value
//            }
//        }
//    }
//
//    override fun put(index: Int, element: E) {
//        require(index < size)
//
//        while (true) {
//            if (index >= core.value.array.size) continue
//
//            val value = core.value.array[index].value
//
//            if (value != null) {
//                val currentValue = core.value.array[index].getAndSet(null)
//                if (currentValue != null) {
//                    core.value.array[index].compareAndSet(null, element)
//                    return
//                }
//            }
//        }
//    }
//
//    override fun pushBack(element: E) {
//        val containerSize = core.value._size.getAndIncrement()
//
//        while (true) {
//            val currentArray = core.value
//
//            if (containerSize >= currentArray.array.size) {
//                val newArray = Core<E>(containerSize * 2)
//                newArray._size.value = containerSize + 1
//
//                if (currentArray.next.compareAndSet(null, newArray)) {
//                    for (i in 0 until currentArray.array.size) {
//
//                        while (true) {
//                            val currentValue = currentArray.array[i].getAndSet(null)
//
//                            if (currentValue != null) {
//                                newArray.array[i].value = currentValue
//                                break
//                            }
//                        }
//                    }
//                    newArray.array[containerSize].value = element
//                    if (core.compareAndSet(currentArray, newArray)) {
//                        return
//                    }
//                }
//            } else {
//                if (currentArray.array[containerSize].compareAndSet(null, element)) {
//                    return
//                }
//            }
//        }
//    }
//
//    override val size: Int
//        get() {
//            return core.value._size.value
//        }
//}
//
//private class Core<E>(
//    capacity: Int,
//) {
//    val array = atomicArrayOfNulls<E>(capacity)
//    val _size = atomic(0)
//    val next: AtomicRef<Core<E>?> = atomic(null)
//
////    val size: Int = _size.value
//
//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < _size.value)
//        return array[index].value as E
//    }
//}
//
//private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

//import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val len = atomic(0)

    override fun get(index: Int): E {
        require(size > index)
        while (true) {
            if (core.value.capacity > index) {
                val x = core.value.array[index].value
                if (x != null)
                    return x
                else
                    continue
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(size > index)
        while (true) {
            if (core.value.capacity > index
                && core.value.array[index].getAndSet(null) != null) {
                core.value.array[index].value = element
                return
            }
        }
    }

    private fun checkAndCopy(newSize: Int, oldCore: Core<E>): Boolean {
        if (newSize < oldCore.capacity) return true
        val newCore = Core<E>(oldCore.capacity * 2)
        if (oldCore.next.compareAndSet(null, newCore)) {
            for (i in 0 until oldCore.capacity)
                while (true) {
                    val element = oldCore.array[i].getAndSet(null)
                    if (element != null) {
                        newCore.array[i].value = element
                        break
                    }
                }
            this.core.value = newCore
        }
        return false
    }

    override fun pushBack(element: E) {
        val oldSize = len.getAndIncrement()
        while (true) {
            val coreVal = this.core.value
            if (!checkAndCopy(oldSize, coreVal)) continue
            if (coreVal.array[oldSize].compareAndSet(null, element)) return
        }
    }

    override val size: Int get() {
        return len.value
    }
}

private class Core<E>(val capacity: Int) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    val array = atomicArrayOfNulls<E>(capacity)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME