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
            a[index].compareAndSet( cop, Value( cop!!.updateA, null, null, null, null, null, null ) )
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
        if (index1 == index2) {
            return cas(index1, expected1, ( expected1 as Int + 2 ) as E )
        }
        if ( index1 == index2 && update1 != update2 ) return false
        val command : Value<E>
        val command1 : Value<E>
        if ( index1 < index2 ) {
            command = Value( expected1, expected2, UNDF, index1, index2, update1, update2 )
            command1 = Value( expected2, expected1, UNDF, index2, index1, update2, update1 )
        } else {
            command = Value( expected2, expected1, UNDF, index2, index1, update2, update1 )
            command1 = Value( expected1, expected2, UNDF, index1, index2, update1, update2 )
        }
        var copy = a[command.indexA!!].value
        var copy1 = a[command.indexB!!].value
        if ( copy!!.a.value == a[ command.indexA ].value!!.a.value && a[command.indexA].compareAndSet( copy, command ) ) {
            if ( copy1!!.a.value == a[command1.indexA!!].value!!.a.value && a[ command1.indexA!! ].compareAndSet( copy1, command1 ) ) {
                command.c = SUCC
                a[command.indexA].compareAndSet( command, Value( command.updateA, null, null, null, null, null, null) )
                a[command.indexB].compareAndSet( command1, Value( command.updateA, null, null, null, null, null, null ) )
                return true
            } else {
                command.c = FAIL
                a[ command.indexA!! ].compareAndSet( command, copy )
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
val KOTLINDERMO : Int = 123446

//
// open class Value<E> (value: E? ) {
//    val valu = atomic( value )
//     override fun equals(other: Any?): Boolean {
//         if ( this === other )
//             return true
//         if ( other !is Value<*> )
//             return false
//         if ( other.valu.value != valu.value )
//             return false
//         return true
//     }
//
//     override fun hashCode(): Int {
//         return valu.hashCode()
//     }
//
// }


class Value<E> (value: E?, valueB : E?, code : Int?, ia : Int?, ib : Int?, uA : E?, uB : E? ) {
    val a = atomic(value)
    val b = valueB
    var c = code
    val indexA = ia
    val indexB = ib
    val updateA = uA
    val updateB = uB

}
