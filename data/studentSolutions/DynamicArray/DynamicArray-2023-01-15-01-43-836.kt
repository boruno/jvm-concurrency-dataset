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
            var sizeC = core.value._size.value
            val capOld = core.value.cap.value
            var copyC = core.value
            var old_core = copyC
            if ( sizeC >= core.value.cap.value ) {
//                var copyC = core.value
//                val old_core = copyC
                val a =  Core<E>( capOld * 2 )
                copyC.next.compareAndSet( null, a )
                if ( copyC.next.value != null ) {
                    copyC = copyC.next.value!!
                }
                copyC._size.compareAndSet ( 0, old_core._size.value )
                for (i in 0 until old_core._size.value) {
                    while (true) {
                        val old_value = old_core.array[i].value as Int
                        if ( old_value % BROKEN_VALUE != 0 ) {
                            if ( old_core.array[i].compareAndSet(old_value as E?, BROKEN_VALUE.times( old_value ) as E))
                                if ( copyC.array[i].compareAndSet(null, old_value as E)) {
                                    break
                                }
                        } else {
                            break
                        }
                    }
                }
//                copyC.array[ old_core._size.value ].compareAndSet( null, element)
                core.compareAndSet(old_core, copyC )
            } else {
                if ( core.value.array[ sizeC ].compareAndSet( null, element ) ) {
                    core.value._size.compareAndSet( sizeC, sizeC + 1 )
                    return
                } else {
                    core.value._size.compareAndSet( sizeC, sizeC + 1 )
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
    val govno = atomicArrayOfNulls<E>(capacity)
    val _size = atomic(0)
    val cap = atomic( capacity )
    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
//        var result = array[index].value as Int
//        var cop = next.value
//        if ( cop != null ) {
//            var valcop = cop.array[ index ].value
//            if ( valcop != null ) {
//                result = valcop as Int
//            }
//        }
//        if ( result % BROKEN_VALUE == 0  ) {
//            return result.div( BROKEN_VALUE ) as E
//        } else {
//            return result as E
//        }
        return array[index].value as E
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val BROKEN_VALUE = 10000
