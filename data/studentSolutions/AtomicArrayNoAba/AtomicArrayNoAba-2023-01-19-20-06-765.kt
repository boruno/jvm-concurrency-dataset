import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = RealValue<E>(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet( RealValue<E>(expected), RealValue<E>(update) )

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        // A всегда меньше B
        var command = ValueCased<E>(null, null, UNDF, null, null, null, null)
        if ( index1 < index2 ) {
            command = ValueCased( expected1, expected2, UNDF, index1, index2, update1, update2 )
        } else {
            command = ValueCased( expected2, expected1, UNDF, index2, index1, update2, update1 )
        }
        if ( a[ command.indexA!! ].compareAndSet( RealValue<E>(command.a), command ) ) {
            if ( a[ command.indexB!! ].compareAndSet( RealValue<E>( command.b ), command ) ) {
                command.c = SUCC
                a[command.indexA!!].compareAndSet( command, RealValue<E>(command.updateA ) )
                a[command.indexB!!].compareAndSet( command, RealValue<E>(command.updateB ) )
                return true
            } else {
                command.c = FAIL
                a[ command.indexA!! ].compareAndSet( command, RealValue<E>(command.a) )
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


 abstract class Value<E> (value: E? ) {
    val value = value

//     override fun equals(other: Any?): Boolean {
//         return ( other is Value<*> && value!! == other.value ) || ( ( other is Int) && other == value as Int )
//     }
}

class RealValue<E>( value: E? ) : Value<E>( value ) {
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
