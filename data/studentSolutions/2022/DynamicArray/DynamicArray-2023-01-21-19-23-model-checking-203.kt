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
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        require(index < fooSize.value)
        val core = getActualCore()
        return core.get(index).getValue()!!
    }

    override fun put(index: Int, element: E) {
        require(index < fooSize.value)
        val core = getActualCore()
        core.set(index, element)
    }

    private fun getActualCore(): Core<E> {
        var curr = core.value

        while (curr.next.value != null) {
            curr = curr.next.value!!
        }

        return curr
    }

    override fun pushBack(element: E) {
        while (true) {
            val currentCore = getActualCore()
            val currSize = fooSize.value

            if (currSize < currentCore.capacity) {
                if (currentCore.cas(currSize, null, ActualRef(element))) { // TODO: все поля должны быть moved, пустых не может быть, moved, только, если места не было
                    fooSize.addAndGet(1)
                    return
                }
                continue
            } else {
                updateCore(currentCore.capacity * 2)
            }
        }
    }

    private fun updateCore(newCoreCap: Int) {
        val currentTable = core.value
        // update core
        currentTable.next.compareAndSet(null, Core<E>(newCoreCap))
        val newCore = currentTable.next.value!!

        // move all elements
        elems@for (i in 0..(currentTable.capacity-1)) {
            foo@while (true) { // ставим moving, потом moved(когда выставили в новую уже), если moving или actual, то ничего не значит продолжаем
                val ref = currentTable.get(i)

                if (ref is MovedRef<E>) {
                    continue@elems
                }

                if (ref is MovingRef<E>) {
                    newCore.set(i, ref.getValue()!!) // set не должен ставить, если moving или moved
                    currentTable.cas(i, ref, MovedRef(ref.getValue()))
                    continue@elems
                }

                if (ref is ActualRef<E>) {
                    val moving = MovingRef(ref.getValue())
                    if (!currentTable.cas(i, ref, moving)) {
                        continue@foo
                    }
                    newCore.set(i, ref.getValue()!!)
                    if (!currentTable.cas(i, moving, MovedRef(moving.getValue()))) {
                        continue@foo
                    } // пофиг, идем на след итерацию, там будет moved и мы выйдем
//                    break@foo
                    continue@elems
                }
            }
        }
    }

    override val size: Int get() = fooSize.value

    val fooSize: AtomicInt = atomic(0)
}

private interface Ref<E> {
    fun getValue(): E?
}

private class ActualRef<E>(val element: E?) : Ref<E> {
    override fun getValue(): E? {
        return element
    }
}

private class MovingRef<E>(val element: E?) : Ref<E> {
    override fun getValue(): E? {
        return element
    }
}

private class MovedRef<E>(val element: E?) : Ref<E> {
    override fun getValue(): E? {
        return element
    }
}

private class Core<E>(
    val capacity: Int,
) {
    val next: AtomicRef<Core<E>?> = atomic(null)
    private val array = atomicArrayOfNulls<Ref<E>>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    //    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Ref<E> {
        val elem = array[index].value

        if (elem is ActualRef<E>) {
            return elem
        } else if (elem is MovingRef<E>) {
            return elem
        } else {
            return next.value!!.get(index)
        }
    }

    fun set(index: Int, element: E) {
        while (true) {
            val curr = array[index].value
            val newNode = ActualRef(element)

            if (curr == null || curr is ActualRef<E>) {
                if (!cas(index, curr, newNode)) {
                    continue
                }
                break
            } else if (curr is MovingRef<E>) { // нужно помочь переместить
                next.value!!.cas(index, null, ActualRef(curr.getValue()))
                if (cas(index, curr, MovedRef(curr.getValue()))) {
                    break
                }
            } else if (curr is MovedRef<E>) {
                next.value!!.set(index, element)
                break
            } else {
                throw AssertionError("never happend in set")
            }
        }
    }

    fun cas(index: Int, expected: Ref<E>?, update: Ref<E>): Boolean {
        return array[index].compareAndSet(expected, update)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME