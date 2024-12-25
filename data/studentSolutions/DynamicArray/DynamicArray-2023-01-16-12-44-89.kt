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
            val coreCope = core.value
            val copy = coreCope.array[ index ].value
            if ( copy != null ) {
                if ( (copy as Int) % BROKEN_VALUE != BROKEN_VALUE1 && ( copy as Int ) % BROKEN_VALUE != ( BROKEN_VALUE - BROKEN_VALUE1 ) && ( copy as Int ) % BROKEN_VALUE != ( BROKEN_VALUE1 - BROKEN_VALUE ) ) {
                    if ( coreCope.array[index].compareAndSet(copy, element)) {
                        return
                    }
                } else {
                    if ( coreCope.next.value != null ) {
                        coreCope.next.value!!.array[index].getAndSet(element)
                        return
                    }
                }
            } else {
                if ( coreCope.next.value != null ) {
                    coreCope.next.value!!.array[index].getAndSet( element)
                } else {
                    coreCope.array[index].compareAndSet( null, element )
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while ( true ) {
            var copyC = core.value
            var sizeC = copyC._size.value
            val capOld = copyC.cap.value
            var old_core = copyC
            if ( sizeC >= core.value.cap.value ) {
                val a =  Core<E>( capOld * 2 )
                copyC.next.compareAndSet( null, a )
                if ( copyC.next.value != null ) {
                    copyC = copyC.next.value!!
                }
                for (i in 0 until old_core._size.value) {
                    while (true) {
                        val old_value = old_core.array[i].value
                        var intValue : Int
                        if ( old_value == null ) {
                            break
                        } else {
                            intValue = old_value as Int
                        }
                        if ( intValue % BROKEN_VALUE != BROKEN_VALUE1 && intValue % BROKEN_VALUE != ( BROKEN_VALUE - BROKEN_VALUE1 ) && intValue % BROKEN_VALUE != ( BROKEN_VALUE1 - BROKEN_VALUE ) ) {
                            if ( old_core.array[i].compareAndSet( intValue as E?, ( BROKEN_VALUE.times( intValue ) + BROKEN_VALUE1 ) as E))
                                if ( copyC.array[i].compareAndSet(null, old_value)) {
                                    break
                                }
                        } else {
                            copyC.array[ i ].compareAndSet( null, old_value )
                            break
                        }
                    }
                }
                core.compareAndSet(old_core, copyC )
            } else {
                if ( core.value.array[ sizeC ].compareAndSet( null, element ) ) {
                    core.value._size.compareAndSet(sizeC, sizeC + 1)
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
    val _size = atomic(capacity/2)
    val cap = atomic( capacity )
    val size: Int = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < _size.value)
        var t = array[ index ].value
        if ( t == null )
            throw IllegalArgumentException()
        var result = t as Int
        var cop = next.value
        if ( cop != null ) {
            var valcop = cop.array[ index ].value
            if ( valcop != null ) {
                result = valcop as Int
            }
        }
       if ( result % BROKEN_VALUE == BROKEN_VALUE1 || result % BROKEN_VALUE == ( BROKEN_VALUE - BROKEN_VALUE1 ) || result % BROKEN_VALUE == ( BROKEN_VALUE1 - BROKEN_VALUE ) ) {
               return ( result - BROKEN_VALUE1).div( BROKEN_VALUE ) as E
        } else {
            return result as E
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val BROKEN_VALUE = 10000
private const val BROKEN_VALUE1 = 500