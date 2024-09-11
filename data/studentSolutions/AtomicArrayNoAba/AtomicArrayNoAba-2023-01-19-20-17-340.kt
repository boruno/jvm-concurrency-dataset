import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value<E>(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.valu.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.valu.compareAndSet( expected, update )

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        // A всегда меньше B
        var command : ValueCased<E>
        if ( index1 < index2 ) {
            command = ValueCased( expected1, expected2, UNDF, index1, index2, update1, update2 )
        } else {
            command = ValueCased( expected2, expected1, UNDF, index2, index1, update2, update1 )
        }
        if ( a[ command.indexA!! ].compareAndSet( Value<E>(command.a), command ) ) {
            if ( a[ command.indexB!! ].compareAndSet( Value<E>( command.b ), command ) ) {
                command.c = SUCC
                a[command.indexA!!].compareAndSet( command, Value<E>(command.updateA ) )
                a[command.indexB!!].compareAndSet( command, Value<E>(command.updateB ) )
                return true
            } else {
                command.c = FAIL
                a[ command.indexA!! ].compareAndSet( command, Value<E>(command.a) )
                return false
            }
        } else {
            return true
        }
    }
}

val SUCC : Int = 10000
val FAIL : Int = 100000
val UNDF : Int = 1000000


 open class Value<E> (value: E? ) {
    val valu = atomic( value )
//     override fun equals(other: Any?): Boolean {
//         return ( other is Value<*> && value!! == other.value ) || ( ( other is Int) && other == value as Int )
//     }
}


class ValueCased<E> (value: E?, valueB : E?, code : Int?, ia : Int?, ib : Int?, uA : E?, uB : E? ) : Value<E>( value ){
    val a = value
    val b = valueB
    var c = code
    val indexA = ia
    val indexB = ib
    val updateA = uA
    val updateB = uB
}
