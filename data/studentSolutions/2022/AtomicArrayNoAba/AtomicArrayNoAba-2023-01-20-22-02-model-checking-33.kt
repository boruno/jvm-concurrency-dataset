import kotlinx.atomicfu.*
import java.lang.Integer.max
import java.lang.Integer.min

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

//    private val descs = atomicArrayOfNulls<desc>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    private inner class Desc(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ) {
        private val okOrder = (index1 < index2)
        val indexA: Int = min(index1, index2)
        val indexB: Int = max(index1, index2)
        val expA = if (okOrder) expected1 else expected2
        val expB = if (okOrder) expected2 else expected1
        val updA = if (okOrder) update1 else update2
        val updB = if (okOrder) update2 else update1
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2)
        if (index1 < index2) {
            if (cas(index1, expected1, update1)) {
                if (cas(index2, expected2, update2)) {
                    return true
                }
                a[index1].value = expected1
            }
        } else {
            if (cas(index2, expected2, update2)) {
                if (cas(index1, expected1, update1)) {
                    return true
                }
                a[index2].value = expected2
            }
        }
//        val desc = Desc(index1, expected1, update1, index2, expected2, update2);
//        if (descs[desc.indexA].compareAndSet(null, desc)) {
//            if (descs[desc.indexB].compareAndSet(null, desc)) {
//                if(cas(desc.indexA, desc.expA, desc.updA)) {
//                    if (cas(desc.indexB, desc.expB, desc.updB)) {
//                        descs[desc.indexA] = null
//                        descs[desc.indexB]
//                        return true
//                    }
//                }
//            }
//        }
        return false
    }
}