import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value<E>(initialValue, null, null, null, null, null, null)
    }

    fun get(index: Int) : E {
        while ( true ) {
            val cop = a[index].value
            if ( cop!!.c.value != null ) {
                complete( cop, index )
                continue
            } else {
                return cop.a.value!!
            }
        }
    }
    fun cas(index: Int, expected: E, update: E) : Boolean {
        while ( true ) {
            var cop = a[index].value!!
            if ( cop.c.value != null ) {
                complete( cop, index )
                continue
            }
            if ( cop.a.value != expected ) {
                return false
            }
            if ( a[ index ].compareAndSet( cop, Value( update, null, null, null, null, null, null ) ) )  {
                return true
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        // A всегда меньше B
        if ( index1 == index2 && update1 != update2 ) return false
        if (index1 == index2) {
            return cas(index1, expected1, ( expected1 as Int + 2 ) as E )
        }
        val command : Value<E>
        var copy = Value<E>( null, null, null, null, null, null, null)
        var copy1 : Value<E> = copy
        if ( index1 < index2 ) {
            command = Value( expected1, expected2, UNDF.value, index1, index2, update1, update2 )
        } else {
            command = Value( expected2, expected1, UNDF.value, index2, index1, update2, update1 )
        }
        copy = a[command.indexA!!].value!!
        copy1 = a[command.indexB!!].value!!
        if ( copy.c.value != null ) {
            complete( copy, command.indexA )
            return false
        }
        if ( copy1.c.value != null ) {
            complete( copy, command.indexB )
            return false
        }

        if ( command.a.value == copy.a.value ) {
            if (a[command.indexA].compareAndSet(copy, command)) {
                return complete(command, command.indexA)
            } else {
                return false
            }
        } else {
            return false
        }
    }

    private fun complete( copy : Value<E>, index: Int) : Boolean {
        while ( true ) {
            if (copy.c.value == SUCC.value) {
                var acopya = a[copy.indexA!!].value
                var acopyb = a[copy.indexB!!].value
                if ( acopya!!.c.value != null ) {
                    if ( acopya.a.value == copy.a.value && acopya.indexB == copy.indexB) {
                        a[copy.indexA!!].compareAndSet(acopya, Value(copy.updateA, null, null, null, null, null, null))
                    }
                }
                if ( acopyb!!.c.value != null ) {
                    if ( acopyb.a.value == copy.a.value && acopyb.indexB == copy.indexB) {
                        a[copy.indexB!!].compareAndSet(acopyb, Value(copy.updateB, null, null, null, null, null, null))
                    }
                }
                return true
            }
            if (copy.c.value == FAIL.value) {
                var acopya = a[copy.indexA!!].value
                var acopyb = a[copy.indexB!!].value
                if ( acopya!!.c.value != null ) {
                    if ( acopya.a.value == copy.a.value && acopya.indexB == copy.indexB) {
                        a[copy.indexA!!].compareAndSet(acopya, Value(copy.a.value, null, null, null, null, null, null))
                    }
                }
                if ( acopyb!!.c.value != null ) {
                    if ( acopyb.a.value == copy.a.value && acopyb.indexB == copy.indexB) {
                        a[copy.indexB!!].compareAndSet(acopyb, Value(copy.b, null, null, null, null, null, null))
                    }
                }
                return false
            }

            var cop = a[copy.indexB!!].value!!
            var cop1 = a[copy.indexA!!].value!!
            if ( cop.c.value != null && ( cop.indexA != cop1.indexA || cop.indexB != cop1.indexB || cop.b != cop1.b || cop.a.value != cop1.a.value ) ) {
//                if ( cop)
                if ( cop.indexB != index )
                    return complete( cop, cop.indexB!! )
                else
                    return complete( cop, cop.indexA!! )
            } else {
                if ( cop.c.compareAndSet( UNDF.value, SUCC.value ) ) {
                    complete(cop, index )
                    return true
                }
            }
            if (copy.c.value == null ) {
                if (a[copy.indexB!!].value!!.a.value == copy.b && a[copy.indexB].compareAndSet(cop, copy) ) {
                    if ( copy.c.compareAndSet( UNDF.value, SUCC.value ) ) {
                        complete(copy, index )
                        return true
                    }
                } else {
                    if ( copy.c.compareAndSet( UNDF.value, FAIL.value ) ) {
                        complete(copy, index )
                        return false
                    }
                }
            }
        }
    }


}

class Value<E> (value: E?, valueB : E?, code : Int?, ia : Int?, ib : Int?, uA : E?, uB : E? ) {
    val a = atomic(value)
    val b = valueB
    val c = atomic(code)
    val indexA = ia
    val indexB = ib
    val updateA = uA
    val updateB = uB
}
val SUCC = atomic( 0 )
val FAIL = atomic( 1 )
val UNDF = atomic( 2 )
