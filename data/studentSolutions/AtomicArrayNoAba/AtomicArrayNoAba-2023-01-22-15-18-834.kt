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

        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        if (index1 == index2) {
            if (expected1 == expected2) return cas(index2, expected2, update2)
            return false
        }
        if (index1 < index2) return hierarchicalCas2(index1, expected1, update1, index2, expected2, update2)

        return hierarchicalCas2(index2, expected2, update2, index1, expected1, update1)
    }
    private fun hierarchicalCas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        return (abstractCas(index1, expected1, update1))
    }
    private fun abstractCas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = get(index)
            if (value != expected) return false
            if (a[index].compareAndSet(expected, update)) return true
        }
    }
}