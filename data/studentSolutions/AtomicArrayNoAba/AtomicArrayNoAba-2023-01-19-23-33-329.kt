import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value<E>(initialValue, null, null, null, null, null, null)
    }

    fun get(index: Int) : E {
        while ( true ) {
            var cop = a[index].value
            if (a[index].value!!.c == SUCC) {
                a[index].compareAndSet( cop, Value( cop!!.updateA, null, null, null, null, null, null ) )
                continue
            }
            return a[index].value!!.a.value!!
        }
    }
    fun cas(index: Int, expected: E, update: E) : Boolean {
        var cop = a[index].value!!
        if ( ( cop.c == null || cop.c == FAIL ) && ( cop.a.value == expected ) ) {
            return a[index].compareAndSet( cop, Value( update, null, null, null, null, null, null ) )
        }
        if ( cop.c == SUCC ) {
            a[index].compareAndSet( cop, Value( cop.updateA, null, null, null, null, null, null ) )
        }
        if ( cop.c == UNDF) {
            var copy1 = a[ cop.indexB!! ].value
            var command1 = Value( cop.b, cop.a.value, cop.c, cop.indexB, cop.indexA, cop.updateB, cop.updateA )
            if ( copy1!!.a.value == a[command1.indexA!!].value!!.a.value && a[ command1.indexA!! ].compareAndSet( copy1, command1 ) ) {
                cop.c = SUCC
                command1.c = SUCC
                a[cop.indexA!!].compareAndSet( cop, Value( cop.updateA, null, null, null, null, null, null) )
                a[command1.indexA!!].compareAndSet( command1, Value( command1.updateA, null, null, null, null, null, null ) )
            } else {
                cop.c = FAIL
                command1.c = FAIL
                a[ cop.indexA!! ].compareAndSet( cop, Value( cop.a.value, null, null, null, null, null, null) )
            }
        }
        return false
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
        val command1 : Value<E>
        var copy = Value<E>( null, null, null, null, null, null, null)
        var copy1 : Value<E> = copy
        if ( index1 < index2 ) {
            command = Value( expected1, expected2, UNDF, index1, index2, update1, update2 )
            command1 = Value( expected2, expected1, UNDF, index2, index1, update2, update1 )
        } else {
            command = Value( expected2, expected1, UNDF, index2, index1, update2, update1 )
            command1 = Value( expected1, expected2, UNDF, index1, index2, update1, update2 )
        }
        copy = a[command.indexA!!].value!!
        copy1 = a[command1.indexA!!].value!!

        if ( copy.c != null ) {
            if ( copy.c == FAIL ) {
                a[command.indexA].compareAndSet( copy, Value( copy.a.value, null, null, null, null, null, null ) )
                return false
            }
            if ( copy.c == SUCC ) {
                a[ command.indexA].compareAndSet( copy, Value( copy.updateA, null, null, null, null, null, null ) )
                return false
            }
            if ( copy.c == UNDF) {
                var copy3 = a[ copy.indexB!! ].value
                var command3 = Value( copy.b, copy.a.value, copy.c, copy.indexB, copy.indexA, copy.updateB, copy.updateA )
                if ( copy3!!.a.value == a[command3.indexA!!].value!!.a.value && a[ command3.indexA!! ].compareAndSet( copy3, command3 ) ) {
                    copy.c = SUCC
                    command3.c = SUCC
                    a[copy.indexA!!].compareAndSet( copy, Value( copy.updateA, null, null, null, null, null, null) )
                    a[command3.indexA!!].compareAndSet( command3, Value( command3.updateA, null, null, null, null, null, null ) )
                } else {
                    copy.c = FAIL
                    command3.c = FAIL
                    a[ copy.indexA!! ].compareAndSet( copy, Value( copy.a.value, null, null, null, null, null, null) )
                }
                return false
            }
        }

        if ( copy1.c != null ) {
            if ( copy1.c == FAIL ) {
                a[command1.indexA].compareAndSet( copy1, Value( copy1.a.value, null, null, null, null, null, null ) )
                return false
            }
            if ( copy1.c == SUCC ) {
                a[ command1.indexA].compareAndSet( copy1, Value( copy1.updateA, null, null, null, null, null, null ) )
                return false
            }
            if ( copy1.c == UNDF) {
                var copy3 = a[ copy1.indexB!! ].value
                var command3 = Value( copy1.b, copy1.a.value, copy1.c, copy1.indexB, copy1.indexA, copy1.updateB, copy1.updateA )
                if ( copy3!!.a.value == a[command3.indexA!!].value!!.a.value && a[ command3.indexA!! ].compareAndSet( copy3, command3 ) ) {
                    copy1.c = SUCC
                    command3.c = SUCC
                    a[copy1.indexA!!].compareAndSet( copy1, Value( copy1.updateA, null, null, null, null, null, null) )
                    a[command3.indexA!!].compareAndSet( command3, Value( command3.updateA, null, null, null, null, null, null ) )
                } else {
                    copy1.c = FAIL
                    command3.c = FAIL
                    a[ copy1.indexA!! ].compareAndSet( copy1, Value( copy1.a.value, null, null, null, null, null, null) )
                }
                return false
            }
        }

        if ( copy.a.value == a[command.indexA!!].value!!.a.value && copy.a.value == command.a.value && a[ command.indexA!! ].compareAndSet( copy, command ) ) {
            if ( copy1.a.value == a[command1.indexA!!].value!!.a.value && copy1.a.value == command1.a.value && a[ command1.indexA ].compareAndSet( copy1, command1 )  ) {
                command.c = SUCC
                command1.c = SUCC
                a[command.indexA].compareAndSet( copy, Value( command.updateA, null, null, null, null, null, null) )
                a[command1.indexA].compareAndSet( copy1, Value( command1.updateA, null, null, null, null, null, null ) )
                return true
            } else {
                command.c = FAIL
                command1.c = FAIL
                a[ command.indexA!! ].compareAndSet( copy, Value( command1.a.value, null, null, null, null, null, null) )
                return false
            }
        } else {
            return false
        }

    }
}

val SUCC : Int = 10000
val FAIL : Int = 100000
val UNDF : Int = 1000000

class Value<E> (value: E?, valueB : E?, code : Int?, ia : Int?, ib : Int?, uA : E?, uB : E? ) {
    val a = atomic(value)
    val b = valueB
    var c = code
    val indexA = ia
    val indexB = ib
    val updateA = uA
    val updateB = uB

}
