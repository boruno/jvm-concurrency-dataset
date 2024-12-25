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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        core.value.put(index, element);
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (!curCore.pushBack(element)) {
                core.compareAndSet(curCore, curCore.resize());
                continue
            }
            return;
        }
    }

    override val size: Int get() = core.value.size
}

private class Core<E>(capacity: Int, size : Int = 0) {
    private val array = atomicArrayOfNulls<Any>(capacity)
    private val _size = atomic(size)
    private val next = atomic<Core<E>?>(null)

    val size: Int
        get() = _size.value

    fun get(index: Int): E {
        require(index < size)
        return when (val element = array[index].value) {
            is Moving<*> -> element.value as E
            Moved -> next.value!!.get(index)
            null -> error("Can't be null")
            else -> element as E
        }
    }

    fun put(index: Int, element: E) {
        require(index < size)
        while(true) {
            when (val oldElem = array[index].value) {
                is Moving<*> -> {
                    move(index);
                    continue;
                }
                Moved -> {
                    next.value!!.put(index, element)
                    return;
                }
                null -> error("Can't be null")
                else -> {
                    if (array[index].compareAndSet(oldElem, element)) {
                        return;
                    }
                }
            }
        }
    }

    fun pushBack(element: E) : Boolean {
        while (true) {
            val index = size
            if (size == array.size) return false;

            if (array[index].compareAndSet(null, element)) {
                _size.compareAndSet(index, index + 1);
                return true;
            } else {
                _size.compareAndSet(index, index + 1);
            }
        }
    }

    fun resize() : Core<E> {
        assert(size == array.size)

        next.compareAndSet(null, Core(array.size * 2, array.size))

        val curNext = next;

        for (i in 0 until size) {
            while (true) {
                when (val element = array[i].value) {
                    Moved -> break
                    is Moving<*> -> move(i)
                    null -> error("not null")
                    else -> array[i].compareAndSet(element, Moving(element))
                }
            }
        }
        return curNext.value!!
    }

    fun move(index : Int) {
        while (true) {
            when (val element = array[index].value) {
                is Moving<*> -> {
                    next.value!!.array[index].compareAndSet(null, element);
                    array[index].value = Moved;
                }
                Moved -> return;
                else -> error("unreachable state")
            }
        }
    }
}

class Moving<E>(val value : E)
object Moved

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME