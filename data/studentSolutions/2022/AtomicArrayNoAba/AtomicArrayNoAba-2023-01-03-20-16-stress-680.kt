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

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

//            (listOf(index1, index2)
//                .zip(listOf(expected1, expected2)))
//                .sortedWith(compareBy { it.first })
//                .forEach {
//                if (a[it.first].value != it.second) {
//                    return false
//                }
//            }

        listOf(index1, index2)
            .zip(listOf(expected1, expected2))
            .zip(listOf(update1, update2)) { a, b -> CasData(a.first, a.second, b) }
            .sortedWith(compareBy { it.index })
            .forEach { casData ->
                if (!a[casData.index].compareAndSet(casData.expected, casData.update)) {
                    return false
                }
            }
        return true

    }

    inner class CasData<E>(
        val index: Int,
        val expected: E,
        val update: E,
    )
}