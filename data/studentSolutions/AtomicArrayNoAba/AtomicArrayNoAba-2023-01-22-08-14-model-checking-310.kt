import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

//
//    fun get(index: Int) : E {
//        while ( true ) {
//            var cop = a[index].value!!
//            if ( cop is AtomicArrayNoAba<*>.DescDCSS || cop is AtomicArrayNoAba<*>.DescCAS2 ) {
//                cop.complete()
//            } else {
//                return cop.value.value as E
//            }
//        }
//    }

    fun get(index : Int ) : E {
        return a[ index ].value!!.get() as E
    }


    fun cas(index: Int, expected : E, update : E ) : Boolean {
        return a[ index ].value!!.cas( expected, update )
    }

//    fun cas(index: Int, expected: E, update: E) : Boolean {
//        while ( true ) {
//            var cop = a[ index ].value!!
//            if ( cop.value.compareAndSet( expected, update ) ) {
//                return true
//            }
//            var valu = cop.value
//            if ( cop is AtomicArrayNoAba<*>.DescCAS2 || cop is AtomicArrayNoAba<*>.DescDCSS ) {
//                cop.complete()
//            } else {
//                if ( valu != expected ) {
//                    return false
//                }
//            }
//        }
//    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if ( index1 == index2 ) {
            if ( expected1 != expected2 ) {
                return false
            } else {
                return cas( index1, expected1, ( expected1 as Int + 2 ) as E )
            }
        }
        var copExp1 = a[index1].value!!
        var copExp2 = a[index2].value!!
        var desc : DescCAS2
        if ( index1 < index2 ) {
            desc = DescCAS2( copExp1, expected1, update1, copExp2, expected2, update2)
            if (!copExp1.cas( expected1, desc ) ) {
                return false
            }
        } else {
            desc = DescCAS2( copExp2, expected2, update2, copExp1, expected1, update1)
            if (!copExp2.cas( expected2, desc ) ) {
                return false
            }
        }
        return desc.complete()
    }


    inner class DescDCSS ( val a: Ref, val expectA: Any?, val updateA: Any?, val b: Ref, val expectB: Any?) {
        val fl : AtomicRef<Boolean?> = atomic(null)
        fun complete() : Boolean {
            fl.compareAndSet( null, b.value.value == expectB )
            if ( fl.value!! ) {
                a.value.compareAndSet( this, updateA )
                return true
            } else {
                a.value.compareAndSet( this, expectA )
                return false
            }
        }
    }

    inner class DescCAS2(
        val a: Ref, val expectA: Any?, val updateA: Any?, val b: Ref, val expectB: Any?, val updateB: Any?
    ) {

        val fl = Ref(null)
        fun complete(): Boolean {
            val descDCSS = DescDCSS( b, expectB, this, fl, null ) // говно+говно*говно
            var res : Boolean
            if ( b.value.value === this || descDCSS.complete() ) {
                fl.value.compareAndSet(null, true)
            } else {
                fl.value.compareAndSet( null, false )
            }
            if ( fl.value.value!! as Boolean ) {
                if (
                !a.value.compareAndSet( this, updateA ) ) throw ExceptionInInitializerError()
                b.value.compareAndSet( this, updateB )
                return true
            } else {
                a.value.compareAndSet( this, expectA )
                b.value.compareAndSet( this, expectB )
                return false
            }
        }

    }

}

open class Ref (initialValue: Any? ) {
    val value = atomic(initialValue)

    fun get() : Any? {
        while (true) {
            var t = value.value
            if ( t is AtomicArrayNoAba<*>.DescCAS2)
                t.complete()
            if ( t is AtomicArrayNoAba<*>.DescDCSS ) {
                t.complete()
            } else {
                return t
            }
        }
    }

    fun cas ( expected : Any?, update : Any? ) : Boolean {
        while ( true ) {
            var valu = value.value
            if ( value.compareAndSet( expected, update) ) {
                return true
            }
            var fl = 0
            if ( valu is AtomicArrayNoAba<*>.DescCAS2 ) {
                valu.complete()
                fl = 1
            }
            if ( valu is AtomicArrayNoAba<*>.DescDCSS ) {
                valu.complete()
                fl = 1
            }
            if ( fl == 0) {
                if ( valu != expected ) {
                    return false
                }
            }
        }
    }

}