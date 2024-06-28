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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index in 0 until size) {
            var current = core.value
            while (true) {
                current[index].let {
                    if (it is Moved) current = current.next.value!!
                    else return it.element
                }
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
                if (current.array[index].compareAndSet(value, Wrapper(element))) break
            }
        } else throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val coreValue = core.value
            while (true) {
                val sizeValue = coreValue.size.value
                if (sizeValue == coreValue.capacity) {
                    coreValue.next.compareAndSet(null, Core(sizeValue * 2, sizeValue))
                    core.compareAndSet(coreValue, coreValue.makeNewTable())
                    continue
                } else if (coreValue.array[sizeValue].compareAndSet(null, Wrapper(element))) {
                    incrementSize(coreValue, sizeValue)
                    return
                } else {
                    incrementSize(coreValue, sizeValue)
                }
            }
        }
    }

    private fun incrementSize(core: Core<E>, size: Int) {
        core.size.compareAndSet(size, size + 1)
    }

    override val size: Int get() = core.value.size.value

}

class Core<E>(val capacity: Int, size: Int = 0) {
    val array: AtomicArray<Wrapper<E>?> = atomicArrayOfNulls(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
    val size: AtomicInt = atomic(size)

    fun makeNewTable(): Core<E> {
        for (index in 0 until size.value) {
            while (true) {
                val value = array[index].value!!
                if (value is Moved) break
                if (value is Raw || array[index].compareAndSet(value, Raw(value.element))) help(index, value)
            }
        }
        return next.value!!
    }

    fun help(index: Int, value: Wrapper<E>) {
        next.value!!.array[index].compareAndSet(null, Wrapper(value.element))
        array[index].compareAndSet(value, Moved(value.element))
    }

    operator fun get(index: Int): Wrapper<E> {
        return array[index].value!!
    }
}

open class Wrapper<E>(val element: E)

class Moved<E>(element: E) : Wrapper<E>(element = element)
class Raw<E>(element: E) : Wrapper<E>(element = element)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME