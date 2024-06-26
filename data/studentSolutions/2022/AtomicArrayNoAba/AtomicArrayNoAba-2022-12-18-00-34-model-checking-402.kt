import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while (true) {
            val curValue = a[index].value!!
            if (curValue is CASNDescriptor) {
                finish(curValue)
                continue
            }
            return curValue as E
        }
    }


    fun cas(index: Int, expected: E, update: E): Boolean {
        val curValue = get(index)
        return curValue == expected && a[index].compareAndSet(curValue, update)
    }


    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val (indexA, indexB) = if (index1 < index2) Pair(index1, index2) else Pair(index2, index1)
        val (expectedA, expectedB) = if (index1 < index2) Pair(expected1, expected2) else Pair(expected2, expected1)
        val (updateA, updateB) = if (index1 < index2) Pair(update1, update2) else Pair(update2, update1)
        assert(indexA < indexB)
        val descriptor = CASNDescriptor(
            indexA, expectedA, updateA,
            indexB, expectedB, updateB
        )
        val curA = get(indexA)
        if (curA != expectedA || !a[indexA].compareAndSet(curA, descriptor)) {
            return false
        }
        return finish(descriptor)
    }

    private fun finish(descriptor: CASNDescriptor): Boolean {
        with(descriptor) {
            val curB = a[indexB].value
            if (curB != expectedB || !a[indexB].compareAndSet(curB, descriptor)) {
                val newB = a[indexB].value
                if (newB != descriptor) {
                    get(indexB)
                    if (outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)) {
                        a[indexB].compareAndSet(descriptor, expectedB)
                        a[indexA].compareAndSet(descriptor, expectedA)
                    }
                    return outcome.value == Outcome.SUCCESS
                }
            }
            assert(outcome.value != Outcome.FAIL)
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            a[indexB].compareAndSet(descriptor, updateB)
            a[indexA].compareAndSet(descriptor, updateA)
            return true
        }
    }

    private class CASNDescriptor(
        val indexA: Int,
        val expectedA: Any?,
        val updateA: Any?,
        val indexB: Int,
        val expectedB: Any?,
        val updateB: Any?,
    ) {
        val outcome = atomic(Outcome.UNDECIDED)
    }

    private enum class Outcome {
        UNDECIDED,
        SUCCESS,
        FAIL,
    }
}
