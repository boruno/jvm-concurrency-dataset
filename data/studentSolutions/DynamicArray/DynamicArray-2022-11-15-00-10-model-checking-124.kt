package mpp.dynamicarray

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


enum class ElementState {
    OK,
    TAKEN_FOR_MIGRATION,
    MIGRATED,
    ALL_RIGHT
}

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
    public val indexFactory = IndexFactory()
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

        var currentCore = core.value
        var i = currentCore.insertToEnd(size, element, this)
        while (i < 0) {
            if (i == FULL) {
                val resized = currentCore.resize()
                if (resized != null) {
                    if (!core.compareAndSet(currentCore, resized)) {
                        currentCore = core.value
                        i = currentCore.insertToEnd(size, element, this)
                        continue
                    }
                    currentCore = core.value
                    i = currentCore.insertToEnd(size, element, this)
                } else {
                    currentCore = currentCore.next.value!!
                    i = currentCore.insertToEnd(size, element, this)
                    continue
                }
            } else if (i == MIGRATED) {
                break
                currentCore = currentCore.next.value!!
                i = currentCore.insertToEnd(size, element, this)
            }
        }

        assert(i >= 0)
        indexFactory.makeSureNotLessThan(i)

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

    fun nextCas(current: Int): Boolean {
        return _i.compareAndSet(current, current + 1)
    }

    fun makeSureNotLessThan(not_less: Int) {
        var current = _i.value
        while (current <= not_less) {
            _i.compareAndSet(current, current + 1)
            current = _i.value
        }
    }

}


private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    private val moved = atomicArrayOfNulls<ElementState>(capacity)

    init {
        for (i in 0 until moved.size) {
            moved[i].value = ElementState.OK
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int, size: Int): E {
        if (index < capacity && moved[index].value != ElementState.MIGRATED) {
            return array[index].value as E
        } else {
            assert(next.value != null)
            return next.value!!.get(index, size)
        }
    }

    fun put(index: Int, value: E, size: Int) {
        if (index >= size) {
            throw IllegalArgumentException()
        }
        if (index < capacity) {
            while (true) {
                val v = array[index].value
                if (moved[index].value == ElementState.OK) {
                    if (!array[index].compareAndSet(v, value)) {
                        continue
                    }
                    if (moved[index].value != ElementState.OK) {
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

    fun insertToEnd(recommendedStart: Int, value: E, arr: DynamicArrayImpl<E>): Int {

        var i : Int = recommendedStart
        while (true) {

            if (i >= capacity) {
                return FULL
            }

            if (!moved[i].compareAndSet(ElementState.OK, ElementState.OK)) {
                return MIGRATED
            }

            if (!array[i].compareAndSet(null, value)) {
                i++
                continue
            }

            if (!moved[i].compareAndSet(ElementState.OK, ElementState.OK)) {
                if (!moved[i].compareAndSet(ElementState.ALL_RIGHT, ElementState.ALL_RIGHT)) {
                    return MIGRATED
                }
            }

            return i
        }

    }

    // --------------

    val next = atomic<Core<E>?>(null)

    fun resize(): Core<E>? {

        val c = Core<E>(capacity * 4)

        if (next.compareAndSet(null, c)) {
            for (i in 0 until capacity) {
                moved[i].compareAndSet(ElementState.OK, ElementState.TAKEN_FOR_MIGRATION)
                val v = array[i].value
                assert(v != null)
                if (c.array[i].compareAndSet(null, v)) {
                    moved[i].compareAndSet(ElementState.TAKEN_FOR_MIGRATION, ElementState.ALL_RIGHT)
                } else {
                    moved[i].compareAndSet(ElementState.TAKEN_FOR_MIGRATION, ElementState.MIGRATED)
                }
            }
        }

        return null

    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME