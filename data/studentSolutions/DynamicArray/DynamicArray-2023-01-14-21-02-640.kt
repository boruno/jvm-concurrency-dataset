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
        while (true) {
            require( index < size )
            val copy = core.value.array[ index ].value
            if ( core.value.array[ index ].value != BROKEN_VALUE ) {
                if ( core.value.array[index].compareAndSet( copy, element ) ) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while ( true ) {
            if ( size >= core.value.cap.value ) {
                val a =  Core<E>( core.value.cap.value * 2 )
                if ( core.value.next.compareAndSet( null, a ) ) {
                    val old_core = core.value
                    for (i in 0 until core.value.size) {
                        while (true) {
                            val old_value = core.value.array[i].value
                            if ( old_value != BROKEN_VALUE ) {
                                if (core.value.array[i].compareAndSet(old_value, BROKEN_VALUE as E)) {
                                    if (core.value.next.value!!.array[i].compareAndSet(null, old_value)) break
                                }
                            } else {
                                break
                            }
                        }
                    }
                core.compareAndSet(old_core, core.value.next.value!!)
                }
            } else {
                if ( core.value.array[ size ].compareAndSet( null, element ) ) {
                    core.value._size.getAndIncrement()
                    return
                }
            }
        }
    }

    override val size: Int get() = core.value._size.value
}

private class Core<E>(
    capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next : AtomicRef<Core<E>?> = atomic( null )
    val _size = atomic(0)
    val cap = atomic( capacity )
    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val BROKEN_VALUE = -1333