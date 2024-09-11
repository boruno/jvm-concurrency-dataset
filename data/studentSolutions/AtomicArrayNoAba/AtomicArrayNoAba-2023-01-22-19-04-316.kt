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

    fun comlpete(){}

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
    var dcss = DescDCSS( b, expectB, this )
    fun complete(): Boolean {
        var IWONNADIEPLEASEKILLME = b.value.value
        if ( IWONNADIEPLEASEKILLME != this ) {
            if ( IWONNADIEPLEASEKILLME is DescDCSS ) {
                IWONNADIEPLEASEKILLME.complete()
            }
            while ( true ) {
                var cop = b.value.value
//                if ( cop is DescDCSS ) {
//                    cop.complete()
//                }
//                if ( cop is DescCAS2 ) {
//                    cop.complete()
//                }
                if (cop != expectB) {
                    res.value.compareAndSet(null, false)
                    break
                }
                 if (b.value.compareAndSet(expectB, dcss)) {
                     dcss.complete()
                     break;
                }
            }
        }
        if ( dcss.res.value.value != null && dcss.res.value.value == true ) {
            res.value.compareAndSet( null, true )
            a.value.compareAndSet( this, updateA )
            b.value.compareAndSet( this, updateB )
            return true
        } else {
            res.value.compareAndSet( null, false )
            a.value.compareAndSet( this, expectA )
            b.value.compareAndSet( this, expectB )
            return false
        }
    }

}

class DescDCSS ( val a: Ref, val expectA: Any?, val descCAS2: DescCAS2 ) {
    val res = Ref(null)
    val fl : AtomicRef<Boolean?> = atomic(null)
    fun complete() : Boolean {
        if ( descCAS2.res.value.value == null ) {
            fl.compareAndSet( null, true )
            this.res.value.compareAndSet( this, descCAS2 )
            return true
        } else {
            return false
        }
    }
}
