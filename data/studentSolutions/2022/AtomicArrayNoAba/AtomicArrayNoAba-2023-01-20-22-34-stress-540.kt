import kotlinx.atomicfu.*
import java.lang.Integer.max
import java.lang.Integer.min

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)


    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        if (a[index].value!! is AtomicArrayNoAba<*>.Desc) {
            return (a[index].value!! as AtomicArrayNoAba<E>.Desc).expA
        }
        return a[index].value!! as E
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    private inner class Desc(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ) {
        private val okOrder = (index1 < index2)
        val indexA: Int = min(index1, index2)
        val indexB: Int = max(index1, index2)
        val expA: E = if (okOrder) expected1 else expected2
        val expB: E = if (okOrder) expected2 else expected1
        val updA: E = if (okOrder) update1 else update2
        val updB: E = if (okOrder) update2 else update1
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2)

        val desc = Desc(index1, expected1, update1, index2, expected2, update2);
        if (a[desc.indexA].compareAndSet(desc.expA, desc)) {
            if (a[desc.indexB].compareAndSet(desc.expB, desc.updB)) {
                a[desc.indexA].value = desc.updA
                return true
            }
            a[desc.indexA].value = desc.expA
        }
        return false
    }
}