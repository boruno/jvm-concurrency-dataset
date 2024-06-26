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
        val curr = updateAllTables()
//        val core = getActualCore()
        return curr.get(index).getValue()!!
    }

    override fun put(index: Int, element: E) {
        require(index < fooSize.value)
        val curr = updateAllTables()
//        val core = getActualCore()
        curr.set(index, element)
    }

    private fun getActualCore(): Core<E> {
        var curr = core.value

        while (curr.next.value != null) {
            curr = curr.next.value!!
        }

        return curr
    }

    override fun pushBack(element: E) {
// таблица обновлена не до конца, один пришел создал новую, обновил часть, и его сняли. Пришел другой, увидел капасити и начал дальше писать, не обновив таблицу, записал себя в след
        while (true) {
            val currentCore = updateAllTables() //
            var currSize = 0

            while (currSize < currentCore.capacity) {
                if (currentCore.cas(currSize, null, ActualRef(element))) { // TODO: все поля должны быть moved, пустых не может быть, moved, только, если места не было
                    fooSize.compareAndSet(currSize, currSize+1)//кто-то мог обновит за нас
                    return
                }
                fooSize.compareAndSet(currSize, currSize+1)
                currSize++
            }
            updateCore(currentCore, currentCore.capacity * 2)
        }
    }

    // return last updated
    private fun updateAllTables(): Core<E> {
        var currTable = core.value

        while (currTable.next.value != null) {
            moveElems(currTable, currTable.next.value!!)
            currTable = currTable.next.value!!
        }

        return currTable
    }

    private fun moveElems(firstTable: Core<E>, second: Core<E>) {
        elems@for (i in 0..(firstTable.capacity-1)) {
            foo@while (true) { // ставим moving, потом moved(когда выставили в новую уже), если moving или actual, то ничего не значит продолжаем
                val ref = firstTable.array[i].value // находит актуальную, не то, что я хочу
//                val ref = currentTable.get(i) // находит актуальную, не то, что я хочу

                if (ref == null) {
                    throw AssertionError("wtf")
                }
                if (ref is MovedRef<E>) {
                    continue@elems
                }

                if (ref is MovingRef<E>) {
                    second.set(i, ref.getValue()!!) // set не должен ставить, если moving или moved
                    firstTable.cas(i, ref, MovedRef(ref.getValue()))
                    continue@elems
                }

                if (ref is ActualRef<E>) {
                    val moving = MovingRef(ref.getValue())
                    if (!firstTable.cas(i, ref, moving)) {
                        continue@foo
                    }
                    second.set(i, ref.getValue()!!)
                    if (!firstTable.cas(i, moving, MovedRef(moving.getValue()))) {
                        continue@foo
                    } // пофиг, идем на след итерацию, там будет moved и мы выйдем
//                    break@foo
                    continue@elems
                }
            }
        }
    }

    private fun updateCore(currentTable: Core<E>, newCoreCap: Int) {
        // update core
        currentTable.next.compareAndSet(null, Core<E>(newCoreCap))
        val newCore = currentTable.next.value!!

        // move all elements
        moveElems(currentTable, newCore)
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
    val array = atomicArrayOfNulls<Ref<E>>(capacity)
    private val _size = atomic(0)

    val size: Int = _size.value

    //    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): Ref<E> {
        val nextV = next.value
        if (index >= capacity && nextV == null) {
            throw IllegalArgumentException("foo")
        } else if (index >= capacity && nextV != null) {
            return nextV.get(index)
        }

        val elem = array[index].value
        if (elem is ActualRef<E>) {
            return elem
        } else if (elem is MovingRef<E>) {
            return elem
        } else if (elem == null && nextV != null) {
            return nextV.get(index)
        } else if (elem == null && nextV == null) {
            throw IllegalArgumentException("wtf")
        } else {
            return next.value!!.get(index)
        }
    }

    fun set(index: Int, element: E) {
        val nextV = next.value
        if (index >= capacity && nextV == null) {
            throw IllegalArgumentException("set1")
        } else if (index >= capacity && nextV != null) {
            nextV.set(index, element)
            return
        }

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