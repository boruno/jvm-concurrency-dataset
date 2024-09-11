import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value<E>(initialValue, null, null, null, null, null, null)
    }

    fun get(index: Int) : E {
        while ( true ) {
            var cop = a[index].value
            if ( cop!!.c.value == UNDF.value || cop.c.value == SUCC.value ) {
                undfComplete( cop, index )
                continue
            }
            return a[index].value!!.a.value!!
        }
    }
    fun cas(index: Int, expected: E, update: E) : Boolean {
        while ( true ) {
            var cop = a[index].value!!
            if ( cop!!.c.value != null ) {
                undfComplete( cop, index )
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
            undfComplete( copy, command.indexA )
            return false
        }
        if ( copy1.c.value != null ) {
            undfComplete( copy, command.indexB )
            return false
        }

//
//        if ( copy.c != null ) {
//            if ( copy.c == FAIL ) {
//                a[command.indexA].compareAndSet( copy, Value( copy.a.value, null, null, null, null, null, null ) )
//                return false
//            }
//            if ( copy.c == SUCC ) {
//                a[ command.indexA].compareAndSet( copy, Value( copy.updateA, null, null, null, null, null, null ) )
//                return false
//            }
//            if ( copy.c == UNDF) {
//                var copy3 = a[ copy.indexB!! ].value
//                var command3 = Value( copy.b, copy.a.value, copy.c, copy.indexB, copy.indexA, copy.updateB, copy.updateA )
//                if ( copy3!!.a.value == a[command3.indexA!!].value!!.a.value && a[ command3.indexA!! ].compareAndSet( copy3, command3 ) ) {
//                    copy.c = SUCC
//                    command3.c = SUCC
//                    a[copy.indexA!!].compareAndSet( copy, Value( copy.updateA, null, null, null, null, null, null) )
//                    a[command3.indexA!!].compareAndSet( command3, Value( command3.updateA, null, null, null, null, null, null ) )
//                } else {
//                    copy.c = FAIL
//                    command3.c = FAIL
//                    a[ copy.indexA!! ].compareAndSet( copy, Value( copy.a.value, null, null, null, null, null, null) )
//                }
//                return false
//            }
//        }
//
//        if ( copy1.c != null ) {
//            if ( copy1.c == FAIL ) {
//                a[command1.indexA].compareAndSet( copy1, Value( copy1.a.value, null, null, null, null, null, null ) )
//                return false
//            }
//            if ( copy1.c == SUCC ) {
//                a[ command1.indexA].compareAndSet( copy1, Value( copy1.updateA, null, null, null, null, null, null ) )
//                return false
//            }
//            if ( copy1.c == UNDF) {
//                var copy3 = a[ copy1.indexB!! ].value
//                var command3 = Value( copy1.b, copy1.a.value, copy1.c, copy1.indexB, copy1.indexA, copy1.updateB, copy1.updateA )
//                if ( copy3!!.a.value == a[command3.indexA!!].value!!.a.value && a[ command3.indexA!! ].compareAndSet( copy3, command3 ) ) {
//                    copy1.c = SUCC
//                    command3.c = SUCC
//                    a[copy1.indexA!!].compareAndSet( copy1, Value( copy1.updateA, null, null, null, null, null, null) )
//                    a[command3.indexA!!].compareAndSet( command3, Value( command3.updateA, null, null, null, null, null, null ) )
//                } else {
//                    copy1.c = FAIL
//                    command3.c = FAIL
//                    a[ copy1.indexA!! ].compareAndSet( copy1, Value( copy1.a.value, null, null, null, null, null, null) )
//                }
//                return false
//            }
//        }
        if ( copy.a.value == a[command.indexA!!].value!!.a.value && copy.a.value == command.a.value && a[ command.indexA!! ].compareAndSet( copy, command ) ) {
            if ( copy1.a.value == a[command.indexB!!].value!!.a.value && copy1.a.value == command.a.value && a[ command.indexB ].compareAndSet( copy1, command )  ) {
                command.c.compareAndSet( UNDF.value, SUCC.value)
                a[command.indexA].compareAndSet( copy, Value( command.updateA, null, null, null, null, null, null) )
                a[command.indexB!!].compareAndSet( copy1, Value( command.updateB, null, null, null, null, null, null ) )
                return true
            } else {
                command.c.compareAndSet( UNDF.value, FAIL.value)
                a[ command.indexA!! ].compareAndSet( copy, Value( command.a.value, null, null, null, null, null, null) )
                return false
            }
        } else {
            return false

        }

    }

    private fun undfComplete( copy : Value<E>, index: Int) {
        while ( true ) {
            if (copy.c.value == SUCC.value) {
                var acopya = a[copy.indexA!!].value
                var acopyb = a[copy.indexB!!].value
                if ( acopya!!.c.value != null ) {
                    if ( acopya.a.value == copy.a.value && acopya.indexB == copy.indexB) {
                        a[copy.indexA!!].compareAndSet(acopya, Value(copy.updateA, null, null, null, null, null, null))
                    }
                }else {
                    return
                }
                if ( acopyb!!.c.value != null ) {
                    if ( acopyb.a.value == copy.a.value && acopyb.indexB == copy.indexB) {
                        a[copy.indexB!!].compareAndSet(acopyb, Value(copy.updateB, null, null, null, null, null, null))
                    }
                }else {
                    return
                }
                return
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
                return
            }

            var cop = a[copy.indexB!!].value!!
            var cop1 = a[copy.indexA!!].value!!
            if ( cop.c.value != null && ( cop.indexA != cop1.indexA || cop.indexB != cop1.indexB || cop.b != cop1.b || cop.a.value != cop1.a.value ) ) {
//                if ( cop)
                if ( cop.indexB != index )
                    undfComplete( cop, cop.indexB!! )
                else
                    undfComplete( cop, cop.indexA!! )
                return
            } else {
                if ( cop.c.compareAndSet( UNDF.value, SUCC.value ) ) {
                    undfComplete(cop, index )
                    return
                }
            }
            if (copy.c.value == null ) {
                if (a[copy.indexB!!].value!!.a.value == copy.b && a[copy.indexB].compareAndSet(cop, copy) ) {
                    if ( copy.c.compareAndSet( UNDF.value, SUCC.value ) ) {
                        undfComplete(copy, index )
                        return
                    }
                } else {
                    if ( copy.c.compareAndSet( UNDF.value, FAIL.value ) ) {
                        undfComplete(copy, index )
                        return
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
