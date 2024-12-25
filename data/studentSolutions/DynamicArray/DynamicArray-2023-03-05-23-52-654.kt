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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        if (index in 0..size) {
            while (true) {
                return when (val value = core.value.get(index)) {
                    is Moved -> {
                        var cur = core.value
                        while (cur.next.value != cur) cur = cur.next.value
                        cur.array[index].value?.let {
                            return it.element
                        }
                        continue
                    }

                    else -> value.element
                }
            }
        } else throw IllegalArgumentException()
    }

    override fun put(index: Int, element: E) {
        if (index in 0..size) {
            var cur = core.value
            while (true) {
                when (val value = cur.get(index)) {
                    is Value -> {
                        if (cur.array[index].compareAndSet(value, Value(element))) break
                    }

                    is Raw -> {
                        cur.help(index, value)
                        continue
                    }

                    else -> cur = cur.next.value
                }
            }
        } else throw IllegalArgumentException()
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            while (true) {
                val oldSize = curCore.size.value
                if (oldSize == curCore.array.size) {
                    if (curCore.next.value == curCore) curCore.next.compareAndSet(
                        curCore,
                        Core(curCore.array.size * 2, curCore.size.value)
                    )
                    core.compareAndSet(curCore, curCore.move())
                    break
                }

                if (curCore.array[oldSize].compareAndSet(null, Value(element))) {
                    curCore.size.compareAndSet(oldSize, oldSize + 1)
                    return
                }
                curCore.size.compareAndSet(oldSize, oldSize + 1)
            }
        }
    }

    override val size: Int
        get() {
            return core.value.size.value
        }
}

class Core<E>(capacity: Int, curSize: Int) {
    val array: AtomicArray<Wrapper<E>?> = atomicArrayOfNulls(capacity)
    val next: AtomicRef<Core<E>> = atomic(this)
    val size: AtomicInt = atomic(curSize)

    fun move(): Core<E> {
        for (index in 0 until size.value) {
            while (true) {
                when (val oldValue = array[index].value!!) {
                    is Moved -> break

                    else -> {
                        if (oldValue is Raw || array[index].compareAndSet(oldValue, Raw(oldValue.element))) {
                            help(index, oldValue)
                        }
                    }
                }
            }
        }
        return next.value
    }

    fun help(index: Int, value: Wrapper<E>) {
        next.value.array[index].compareAndSet(null, Value(value.element))
        array[index].compareAndSet(value, Moved(value.element))
    }

    fun get(index: Int): Wrapper<E> {
        return array[index].value!!
    }
}

abstract class Wrapper<E>(val element: E)

class Moved<E>(element: E) : Wrapper<E>(element = element)
class Value<E>(element: E) : Wrapper<E>(element = element)
class Raw<E>(element: E) : Wrapper<E>(element = element)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME