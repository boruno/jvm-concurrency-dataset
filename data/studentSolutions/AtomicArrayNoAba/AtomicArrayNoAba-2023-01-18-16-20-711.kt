import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        var indexx1 : Int
        var indexx2 : Int
        var expectedd1 : E
        var expectedd2 : E
        var updatee1 : E
        var updatee2 : E


        if ( index1 < index2 ) {
            indexx1 = index1
            indexx2 = index2
            expectedd1 = expected1
            expectedd2 = expected2
            updatee1 = update1
            updatee2 = update2
        } else {
            indexx2 = index1
            indexx1 = index2
            expectedd2 = expected1
            expectedd1 = expected2
            updatee2 = update1
            updatee1 = update2
        }
        var succFlag = SUCC.div( 2 )
        var failFlag = FAIL.div( 2 )
        var undfFlag = UNDF.div( 2 )
        var val1 = a[ indexx1 ].value
        if ( ( val1 as Int ) > succFlag )
            a[ indexx1 ].compareAndSet( val1, ( val1 - SUCC ) as E? )
        var val2 = a[indexx2].value
        if ( ( val2 as Int ) > succFlag )
            a[indexx2].compareAndSet( val2, ( val2 - SUCC ) as E? )
        if ( ( val1 as Int ) > failFlag && ( val1 as Int) < undfFlag )
            a[ indexx1 ].compareAndSet( val1, ( val1 - FAIL ) as E? )
        if ( ( val2 as Int ) > failFlag && ( val2 as Int) < undfFlag )
            a[ indexx2 ].compareAndSet( val2, ( val2 - FAIL ) as E? )
        var desc1 = expectedd1 as Int + UNDF
        var desc2 = expectedd2 as Int + UNDF
        if ( a[ indexx1 ].compareAndSet( expectedd1, desc1 as E? ) ) {
            if ( a[indexx2 ].compareAndSet( expectedd2, desc2 as E? ) ) {
                desc1 = updatee1 as Int + SUCC
                desc2 = updatee2 as Int + SUCC
                a[ indexx1 ].getAndSet( desc1 as E? )
                a[ indexx2 ].getAndSet( desc2 as E? )
                a[ indexx1 ].compareAndSet( desc1, updatee1 )
                a[ indexx2 ].compareAndSet( desc2, updatee2 )
                return true
            } else {
                desc1 = expectedd1 as Int + FAIL
                a[ indexx1 ].getAndSet( desc1 as E? )
                a[ indexx1 ].compareAndSet( desc1, expectedd1 )
                return false
            }
        } else {
            return false
        }
    }
}

class Ref <T>( initial: T ) {
    val v = atomic<Any?>(initial)
    val code : Int = 0
    var value: T
        get() {
            return v.value as T
        }
    set (upd) {
        v.value = upd
    }

}
val SUCC : Int = 10000
val FAIL : Int = 100000
val UNDF : Int = 1000000
// + 100k = undf
// + 10k  = fail
// + 1k   = succ