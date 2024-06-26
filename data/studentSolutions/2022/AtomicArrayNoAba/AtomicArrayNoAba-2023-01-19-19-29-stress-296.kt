import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Value<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Value<E>(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.value.compareAndSet( expected, update)

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
        if ( a[ command.indexA.value!! ].compareAndSet( Value<E>(command.a.value), command ) ) {
            if ( a[ command.indexB.value!! ].compareAndSet( Value<E>( command.b.value ), command ) ) {
                command.c.compareAndSet( UNDF, SUCC )
                a[command.indexA.value!!].compareAndSet( command, Value<E>(command.updateA.value))
                a[command.indexB.value!!].compareAndSet( command, Value<E>(command.updateB.value))
                return true
            } else {
                command.c.compareAndSet( UNDF, FAIL )
                a[ command.indexA.value!! ].compareAndSet( command, Value<E>(command.a.value) )
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


 open class Value<E> (valueA: E? ) {
    val value = atomic(valueA)
}
class ValueCased<E> (valueA: E?, valueB : E?, code : Int?, ia : Int?, ib : Int?, uA : E?, uB : E? ) : Value<E>( valueA ){
    val a = atomic(valueA)
    val b = atomic(valueB)
    val c = atomic(code)
    val indexA = atomic(ia)
    val indexB = atomic(ib)
    val updateA = atomic(uA)
    val updateB = atomic(uB)
}
