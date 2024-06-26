import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.get() as Int

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas( expected, update )

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if ( index1 == index2 && expected1 != expected2 ) {
            return false
        }
        if ( index1 == index2 ) {
            cas( index1, expected1, ( expected1 as Int + 2 ) as E )
        }
        var copA = a[ index1 ].value!!
        var copB = a[ index2 ].value!!
        val desc : Desc
        if ( index1 > index2 ) {
            desc = Desc( a[index1].value!!, expected1, update1, a[index2].value!!, expected2, update2, null )
            if ( copA.cas( expected1, desc )) {

            } else {
                return false
            }
        } else {
            desc = Desc( a[index2].value!!, expected2, update2, a[index1].value!!, expected1, update1, null )
            if ( copB.cas( expected2, desc )) {

            } else {
                return false
            }
        }
        return desc.cas2()
    }
}

open class Ref (initialValue: Any? ) {
    val value = atomic( initialValue )


    fun get() : Any? {
        while (true){
            if ( this is Desc ) {
                throw ExceptionInInitializerError()
                complete()
            } else {
                return value.value
            }
        }
    }

    fun set ( update : Any? ) {
        while (true ) {
            if ( this is Desc )
                complete()
            else {
                var cop = value.value
                if ( value.compareAndSet( cop, update) ) {
                    return
                }
            }
        }
    }

    fun cas( expected : Any?, found : Any? ) : Boolean {
        while ( true ) {
            if ( value.compareAndSet( expected, found ) ) {
                return true
            }

        }
    }

}

class Desc (val a : Ref, val expectA : Any?, val updateA : Any?, val b : Ref, val expectB : Any?, val updateB : Any?,
            initialValue: Any?
)  : Ref(initialValue) {

    val fl : AtomicRef<Boolean?> = atomic( null )
    fun cas2 () : Boolean{
        if ( b == this  || dcss( b, expectB, this, a, expectA ) ) {
            throw ExceptionInInitializerError()
            fl.compareAndSet( null, true)
        } else {
            fl.compareAndSet( null, false )
        }
        if ( fl.value!= null ) {
            if (fl.value!!) {
                a.value.compareAndSet(this, updateA)
                b.value.compareAndSet(this, updateB)
            } else {
                a.value.compareAndSet( this, expectA)
                b.value.compareAndSet( this, expectB)
            }
            return fl.value!!
        }
        return false
    }

    fun dcss( a: Ref, expectA: Any?, updateA: Any?, b : Ref, expectB: Any?) : Boolean {
        if ( fl.compareAndSet( null, b.get() == expectB ) ) {
            a.value.compareAndSet( this, updateA )
        } else {
            a.value.compareAndSet( this, expectA )
        }
        return fl.value!!
    }

    fun complete() {
        var t = ( b.get() == expectB )
        if (fl.compareAndSet(null, t )) {
            a.value.compareAndSet(this, updateA)
        } else {
            a.value.compareAndSet(this, expectA)
        }
    }

}