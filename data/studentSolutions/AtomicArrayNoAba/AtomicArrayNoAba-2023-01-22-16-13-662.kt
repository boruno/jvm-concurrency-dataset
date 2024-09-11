import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index : Int ) : E {
        return a[ index ].value!!.get() as E
    }


    fun cas(index: Int, expected : E, update : E ) : Boolean {
        return a[ index ].value!!.cas( expected, update )
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if ( index1 == index2 ) {
            if ( expected1 != expected2 )
                return false
            return cas( index1, expected1, ( expected1 as Int + 2 ) as E )
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


}

open class Ref (initialValue: Any? ) {
    val value = atomic(initialValue)

    fun get() : Any? {
        while (true) {
            val t = value.value
            if ( t is DescCAS2) {
                t.complete()
                continue
            }
            if ( t is DescDCSS ) {
                t.complete()
                continue
            }
            return t
        }
    }

    fun cas ( expected : Any?, update : Any? ) : Boolean {
        while ( true ) {
            if ( value.compareAndSet( expected, update) ) {
                return true
            }
            val valu = value.value
            if ( valu is DescCAS2 ) {
                valu.complete()
                continue
            }
            if ( valu is DescDCSS ) {
                valu.complete()
                continue
            }
            if ( valu != expected ) {
                return false
            }
        }
    }

}

class DescCAS2(
    val a: Ref, val expectA: Any?, val updateA: Any?, val b: Ref, val expectB: Any?, val updateB: Any?
) {

    val res = Ref(null)
    fun complete(): Boolean {
        val descDCSS = DescDCSS( b, expectB, this, res, null ) // говно+говно*говно
        if ( b.value.value == this ) {
            res.value.compareAndSet(null, true )
        } else {
            descDCSS.complete()
            res.value.compareAndSet(null, descDCSS.res.value.value as Boolean)
        }
        if ( res.value.value!! as Boolean ) {
            a.value.compareAndSet( this, updateA )
            b.value.compareAndSet( this, updateB )
            return true
        } else {
            a.value.compareAndSet( this, expectA )
            b.value.compareAndSet( this, expectB )
            return false
        }
    }

}



class DescDCSS ( val a: Ref, val expectA: Any?, val updateA: Any?, val b: Ref, val expectB: Any?) {
    val res = Ref(null)
    fun complete() : Boolean {
        if ( a.value.value !is DescDCSS) {
            if (!a.value.compareAndSet(expectA, this)) {
//                res.value.compareAndSet( null, false)
                return false
            }
        }
        val fl = b.value.value == expectB
        res.value.compareAndSet( null, fl )
        if (res.value.value as Boolean) {
            a.value.compareAndSet(this, updateA)
            return true
        } else {
            a.value.compareAndSet(this, expectA)
            return false
        }
    }
}
