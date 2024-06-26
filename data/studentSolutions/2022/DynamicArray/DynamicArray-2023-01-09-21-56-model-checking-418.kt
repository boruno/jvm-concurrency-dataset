package mpp.dynamicarray
//
import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference
//
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
//
//sealed interface Value {
//    data class NonFixedValue<E>(val value: E) : Value
//    data class FixedValue<E>(val value: E) : Value
//    object Moved : Value
//}
//
//@Suppress("UNCHECKED_CAST")
//class DynamicArrayImpl<E> : DynamicArray<E> {
//    private val core = atomic(Core<Value>(INITIAL_CAPACITY))
//    private val containerSizeCounter = atomic(0)
//
//    override fun get(index: Int): E {
//        require(index < size)
//
//        while (true) {
//            if (index >= core.value.array.size) continue
//            val value = core.value.array[index].value
//
//            when (value) {
//                is Value.FixedValue<*> -> return value.value as E
//                is Value.NonFixedValue<*> -> return value.value as E
//                else -> { continue }
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
//                val before = core.value.array[index].getAndSet(null)
//                if (before != null) {
//                    core.value.array[index].compareAndSet(null, Value.NonFixedValue(element))
//                    return
//                }
//            }
//        }
//    }
//
//    override fun pushBack(element: E) {
//        val containerSize = containerSizeCounter.getAndIncrement()
//
//        while (true) {
//            val currentArray = core.value
//
//            if (containerSize >= currentArray.array.size) {
//                val newArray = Core<Value>(currentArray.size * 2)
//
//                if (core.value.next.compareAndSet(null, newArray)) {
//                    for (i in 0 until currentArray.size) {
//                        val currentValue = currentArray.array[i].value
//
//                        if (currentValue != null) {
//                            if (core.value.array[i].compareAndSet(currentValue, Value.FixedValue(currentValue))) {
//                                newArray.array[i].value = currentValue
//                            }
//                        } else {
//                            core.value.array[i].compareAndSet(null, Value.Moved)
//                        }
//                    }
//                    newArray.array[currentArray.size].value = Value.NonFixedValue(element)
//                    if (core.compareAndSet(currentArray, newArray)) {
//                        return
//                    }
//                }
//            } else {
//                currentArray.array[containerSize].compareAndSet(null, Value.NonFixedValue(element))
//                return
//            }
//        }
//    }
//
//    override val size: Int
//        get() {
//            return containerSizeCounter.value
//        }
//}
//
//private class Core<E>(
//    capacity: Int,
//) {
//    val array = atomicArrayOfNulls<E>(capacity)
//    val _size = atomic(capacity)
//    val next: AtomicRef<Core<Value>?> = atomic(null)
//
//    val size: Int = _size.value
//
//    @Suppress("UNCHECKED_CAST")
//    fun get(index: Int): E {
//        require(index < size)
//        return array[index].value as E
//    }
//}
//
//private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME

//import kotlinx.atomicfu.*
//import java.util.concurrent.atomic.AtomicReference

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val atomicSize = atomic(0)

    override fun get(index: Int): E {
        require(index < size)
        while (true) {
            if (index >= core.value.array.size) continue
            val answer = core.value.array[index].value
            if (answer != null) {
                return answer
            }
        }
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            if (index >= core.value.array.size) continue
            val value = core.value.array[index].value
            if (value != null) {
                val before = core.value.array[index].getAndSet(null)
                if (before != null) {
                    core.value.array[index].compareAndSet(null, element)
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val oldSize = atomicSize.getAndIncrement()
        while (true) {
            val coreValue = core.value
            if (oldSize < coreValue.array.size) {
                if (coreValue.array[oldSize].compareAndSet(null, element)) {
                    return
                }
            } else {
                val newCore = Core<E>(coreValue.array.size * 2)
                if (coreValue.next.compareAndSet(null, newCore)) {

                    for (i in 0 until coreValue.array.size) {
                        while (true) {

                            val value = coreValue.array[i].getAndSet(null)
                            if (value != null) {
                                newCore.array[i].value = value
                                break
                            }
                        }
                    }
                    this.core.value = newCore
                }
            }
        }
    }

    override val size: Int get() {
        return atomicSize.value
    }
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = AtomicReference<Core<E>>()
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
