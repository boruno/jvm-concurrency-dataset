package mpp.dynamicarray

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


enum class ElementState {
    AWAITS_MIGRATION,

}

val RACE = -1
val MIGRATED = -2
val FULL = -3

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
    private val indexFactory = IndexFactory()

    override val size: Int get() = indexFactory.current()

    override fun get(index: Int): E {
        require(index < size)
        return core.value.get(index, size)
    }

    override fun put(index: Int, element: E) {
        require(index < size)
        core.value.put(index, element, size)
    }

    override fun pushBack(element: E) {
        //val i = indexFactory.next()

        var currentCore = core.value
        var proposition = size
        var i = currentCore.insertToEnd(proposition, element)
        while (i < 0) {
            if (i == FULL) {
                if (currentCore.next.value != null) {
                    currentCore = currentCore.next.value!!
                    i = currentCore.insertToEnd(proposition, element)
                    continue
                } else {
                    val newCore = core.value.resize()
                    if (!core.compareAndSet(currentCore, newCore)) {
                        currentCore = core.value
                        i = currentCore.insertToEnd(proposition, element)
                        continue
                    }
                    currentCore = core.value
                    i = currentCore.insertToEnd(proposition, element)
                }


            } else if (i == MIGRATED) {
                //if (currentCore.next.value != null) {
                currentCore = currentCore.next.value!!
                i = currentCore.insertToEnd(proposition, element)
                //}
            } else if (i == RACE) {
                proposition++
                i = currentCore.insertToEnd(proposition, element)
            }
        }

        indexFactory.next()
        //assert( i == indexFactory.next() )

    }

}

class IndexFactory() {

    private val _i = atomic(0)

    fun next(): Int {
        return _i.getAndIncrement()
    }

    fun current(): Int {
        return _i.value
    }

}


private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val moved = atomicArrayOfNulls<Boolean?>(capacity)
    //private val _size = atomic(0)
    //private val _q = atomic(0)

    // size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int, size: Int): E {
//        if (index < size) {
        if (moved[index].value != true) {
            if (array[index].value == null) {
                val o = 12324
            }
            return array[index].value as E
        } else {
            assert(next.value != null)
            return next.value!!.get(index, size)
        }
//        } else {
//            if (next.value != null) {
//                return next.value!!.get(index, size)
//            }
//            throw IllegalArgumentException()
//        }
    }

    fun put(index: Int, value: E, size: Int) {
        if (index >= size) {
            throw IllegalArgumentException()
        }
        if (index < capacity) {
            while (true) {
                val v = array[index].value
                if (moved[index].value == null) {
                    if (!array[index].compareAndSet(v, value)) {
                        continue
                    }
                    if (moved[index].value != null) {
                        continue
                    }
                    break
                } else {
                    assert(next.value != null)
                    next.value!!.put(index, value, size)
                    break
                }
            }
        } else {
            if (next.value != null) {
                return next.value!!.put(index, value, size)
            }
            throw IllegalArgumentException()
        }
    }


//    fun getLatestCore() : Core<E> {
//        var c = this
//        while (c.next.value != null) {
//            c = c.next.value!!
//        }
//        return c
//    }

//    fun getIndexToPushBack() : Int? {
//        var q: Int
//        do {
//            q = _q.value
//            if (!(q + 1 <= capacity)) {
//                return null
//            }
//            if (next.value != null) {
//                return null
//            }
//        } while (!_q.compareAndSet(q, q + 1))
//        return q
//    }

    fun insertToEnd(i: Int, value: E): Int {

        //var i = size

        //val i = getIndexToPushBack() ?: return false
        //val i = size
        while (true) {
            if (i >= capacity) {
                return FULL
            }

            if (!moved[i].compareAndSet(null, null)) {
                return MIGRATED
            }

            val c = this
            if (!c.array[i].compareAndSet(null, value)) {
//                i++
//                continue
                return RACE
            }

            if (!moved[i].compareAndSet(null, null)) {
                return MIGRATED
            }

            //c._size.getAndIncrement()
            return i
        }


    }

    // --------------

    val next = atomic<Core<E>?>(null)

    fun resize(): Core<E> {
        // val s = _size.value
        val c = Core<E>(capacity * 4)
//        c._size.compareAndSet(0, s)
//        c._q.compareAndSet(0, s)

        if (next.compareAndSet(null, c)) {

//            var q : Int
//            do {
//                q = _q.value
//            } while (!_q.compareAndSet(q, -1))

            for (i in 0 until capacity) {
                // mark as not available to write
                // since here readers can read from this, no write
                moved[i].compareAndSet(null, false)
                val v = array[i].value
                assert(v != null)
                // copy value knowing nobody gonna change it here
                // p.s. put
                c.array[i].compareAndSet(null, v)
                // mark this element  as migrated
                // readers must read from next and write to next
                moved[i].compareAndSet(false, true)
            }
        }
        return next.value!!
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME