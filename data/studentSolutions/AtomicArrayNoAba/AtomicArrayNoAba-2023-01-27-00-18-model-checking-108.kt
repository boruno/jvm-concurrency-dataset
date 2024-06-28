import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : E = a[index].loop {
        a[index].value!!
    }

    fun cas(index: Int, expected: Int, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: Int, update1: E,
             index2: Int, expected2: Int, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        return when (index1) {
            index2 -> {
                cas(index1, expected1, (expected1 + 2) as E)
            }
            else -> when {
                index1 < index2 -> cas22(index1, expected1, update1, index2, expected2, update2)
                else -> cas22(index2, expected2, update2, index1, expected1, update1)
            }
        }
    }

    private fun cas22(index1: Int, expected1: Int, update1: E, index2: Int, expected2: Int, update2: E): Boolean {
        val ds = DS(index1, expected1, update1, index2, expected2, update2)
        if (a[index1].compareAndSet(expected1, ds)) {
            ds.complete()
            return ds.dss.value == DSS.COOL
        }
        return false
    }

    private enum class DSS {
        COOL, NONE, OOPS
    }
    private inner class DS(
        val index1: Int, val expected1: Int, val update1: E,
        val index2: Int, val expected2: Int, val update2: E
    ) {
        val dss = atomic(DSS.NONE)
        fun complete() {
            a[index2].compareAndSet(expected2, this)
            when {
                a[index2].value === this -> {
                    dss.compareAndSet(DSS.NONE, DSS.COOL)
                    a[index1].compareAndSet(this, update1)
                    a[index2].compareAndSet(this, update2)
                }
                else -> {
                    dss.compareAndSet(DSS.NONE, DSS.OOPS)
                    a[index1].compareAndSet(this, expected1)
                    a[index2].compareAndSet(this, expected2)
                }
            }
        }
    }
}