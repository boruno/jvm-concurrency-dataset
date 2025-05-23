//package mpp.dynamicarray

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

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

    override fun get(index: Int): E = core.value.get(index)

    override fun put(index: Int, element: E) {
        require(index < size)
        core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        //val i = indexFactory.next()

        var currentCore = core.value
        var i = currentCore.insertToEnd(999, element)
        while (i < 0) {
            if (currentCore.size == currentCore.capacity) {
                val newCore = core.value.resize()
                core.compareAndSet(currentCore, newCore)
                currentCore = core.value
            }
            i = currentCore.insertToEnd(999, element)
        }

        assert( i == indexFactory.next() )

    }

    fun size(): Int {
        return core.value.size
    }
}

class IndexFactory() {

    private val _i = atomic(0)

    fun next() : Int {
        return _i.getAndIncrement()
    }

    fun current() : Int {
        return _i.value
    }

}


private class Core<E>(
    val capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val moved = atomicArrayOfNulls<Boolean?>(capacity)
    private val _size = atomic(0)
    private val _q = atomic(0)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        if (index < size) {
            if (moved[index].value != true) {
                require(index < size)
                return array[index].value as E
            } else {
                assert(next.value != null)
                return next.value!!.get(index)
            }
        } else {
            if (next.value != null) {
                return next.value!!.get(index)
            }
            throw IllegalArgumentException()
        }
    }

    fun put(index: Int, value: E) {
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
                    next.value!!.put(index, value)
                    break
                }
            }
        } else {
            if (next.value != null) {
                return next.value!!.put(index, value)
            }
            throw IllegalArgumentException()
        }
    }


    fun getLatestCore() : Core<E> {
        var c = this
        while (c.next.value != null) {
            c = c.next.value!!
        }
        return c
    }

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

    fun insertToEnd(_i: Int, value: E): Int {

        val FAILED = -1

        //val i = getIndexToPushBack() ?: return false
        val i = size

        if (i >= capacity) {
            return FAILED
        }

        if (!moved[i].compareAndSet(null, null)) {
            return FAILED
        }

        val c = this
        if (!c.array[i].compareAndSet(null, value)) {
            return FAILED
        }

        if (!moved[i].compareAndSet(null, null)) {
            return FAILED
        }

        c._size.getAndIncrement()
        return i
    }

    // --------------

    val next = atomic<Core<E>?>(null)

    fun resize(): Core<E> {
        val s = _size.value
        val c = Core<E>(capacity * 2)
        c._size.compareAndSet(0, s)
        c._q.compareAndSet(0, s)

        if (next.compareAndSet(null, c)) {

            var q : Int
            do {
                q = _q.value
            } while (!_q.compareAndSet(q, -1))

            for (i in 0 until capacity) {
                // mark as not available to write
                // since here readers can read from this, no write
                if ( i >= moved.size) {
                    print(2)
                }
                moved[i].compareAndSet(null, false)
                val v = array[i].value
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