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

    override fun get(index: Int): E {
        if (index in 0 until size) {
            var current = core.value
            while (true) current[index].let {
                if (it !is Moved) return it.element
                else current = current.next.value!!
            }
        } else throw IllegalArgumentException()
    }

    override fun put(index: Int, element: E) {
        if (index in 0 until size) {
            var current = core.value
            while (true) {
                val value = current[index]
                if (value is Raw) current.help(index, value)
                if (value is Moved) current = current.next.value!!
                if (current.cas(index, value, Wrapper(element))) return
            }
        } else throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        loop@ while (true) {
            val current = core.value
            var isDone = false
            while (true) {
                val sizeValue = current.size.value
                if (current.capacity == current.size.value) {
                    if (current.next.compareAndSet(null, Core(current.size.value * 2))) {
                        loopFor@ for (index in 0 until current.size.value) {
                            while (true) {
                                val value = current[index]
                                if (value is Moved) continue@loopFor
                                if (value is Raw || current.cas(index, value, Raw(value.element))) current.help(
                                    index, value
                                )
                            }
                        }
                        core.compareAndSet(current, current.next.value!!)
                    }
                    continue@loop
                }
                if (current.cas(sizeValue, null, Wrapper(element))) isDone = true
                incrementSize(current, sizeValue)
                if (isDone) return
            }
        }
    }

    private fun incrementSize(core: Core<E>, size: Int) {
        core.size.compareAndSet(size, size + 1)
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    val capacity: Int,
) {
    val size: AtomicInt = atomic(capacity / 2)
    val next: AtomicRef<Core<E>?> = atomic(null)
    private val array = atomicArrayOfNulls<Wrapper<E>?>(capacity)

    fun help(index: Int, value: Wrapper<E>) {
        next.value!!.cas(index, null, Wrapper(value.element))
        cas(index, value, Moved(value.element))
    }

    fun cas(index: Int, expected: Wrapper<E>?, update: Wrapper<E>): Boolean {
        return array[index].compareAndSet(expected, update)
    }

    operator fun get(index: Int) = array[index].value!!
}

open class Wrapper<E>(val element: E)

class Moved<E>(element: E) : Wrapper<E>(element = element)
class Raw<E>(element: E) : Wrapper<E>(element = element)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME