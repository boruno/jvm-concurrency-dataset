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
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        if (index < 0 || index >= size) throw IllegalArgumentException()

        while (true) {
            return when (val oldValue = core.value.array[index].value!!) {
                is Moved<E> -> {
                    var cur = core.value
                    while (cur.next.value != cur) {
                        cur = cur.next.value
                    }
                    cur.array[index].value?.let {
                        return it.element
                    }
                    continue
                }

                else -> oldValue.element
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index < 0 || index >= core.value.size.value) throw IllegalArgumentException()
        var cur = core.value
        while (true) {
            when (cur.array[index].value!!) {
                is Common -> {
                    if (cur.array[index].compareAndSet(cur.array[index].value!!, Common(element))) break
                }

                is Moving -> {
                    cur.help(index, cur.array[index].value!!)
                    continue
                }

                else -> cur = cur.next.value
            }
        }
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

                if (curCore.array[oldSize].compareAndSet(null, Common(element))) {
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
                        if (oldValue is Moving || array[index].compareAndSet(oldValue, Moving(oldValue.element))) {
                            help(index, oldValue)
                        }
                    }
                }
            }
        }
        return next.value
    }

    fun help(index: Int, value: Wrapper<E>) {
        next.value.array[index].compareAndSet(null, Common(value.element))
        array[index].compareAndSet(value, Moved(value.element))
    }
}

abstract class Wrapper<E>(val element: E)

class Common<E>(element: E) : Wrapper<E>(element = element)
class Moving<E>(element: E) : Wrapper<E>(element = element)
class Moved<E>(element: E) : Wrapper<E>(element = element)

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME