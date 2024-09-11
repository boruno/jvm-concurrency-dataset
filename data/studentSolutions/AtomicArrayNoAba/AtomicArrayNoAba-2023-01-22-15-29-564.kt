import kotlinx.atomicfu.*
import kotlinx.atomicfu.AtomicRef

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        val res = a[index].value!!
        if (res is CAS2Descriptor<*>) {
            if (res.indexA == index) return res.expectedA as E
            else return res.expectedB as E
        } else if (res is DCSSDescriptor<*>) {
            return res.expectedA as E
        } else {
            return res as E
        }
    }

    fun cas(index: Int, expected: Any, update: Any) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (indexA == indexB) return cas(indexA, expectedA as Any, updateA as Any)
        val cas2desc = CAS2Descriptor<E>(indexA, expectedA, updateA, indexB, expectedB, updateB)
        if (cas(indexA, expectedA as Any, cas2desc)) {
            val outcome = dcss(cas2desc.indexB, cas2desc.expectedB as Any, cas2desc, cas2desc.outcome.value, null)
            if (outcome) {
                cas2desc.outcome.compareAndSet(null, true)
                cas(cas2desc.indexA, cas2desc as Any, cas2desc.updateA as Any)
                cas(cas2desc.indexB, cas2desc as Any, cas2desc.updateB as Any)
                return true
            }
            cas2desc.outcome.compareAndSet(null, false)
            cas(cas2desc.indexA, cas2desc as Any, cas2desc.expectedA as Any)
            return false
        } else {
            cas2desc.outcome.compareAndSet(null, false)
            return false
        }
    }

    fun dcss(
        indexA: Int, expectedA: Any, updateA: Any,
        valB: Boolean?, expectedB: Boolean?
    ): Boolean {
        val dcssDesc = DCSSDescriptor<E>(indexA, expectedA, updateA, valB, expectedB)
        if (cas(indexA, expectedA as Any, dcssDesc)) {
            if (valB == expectedB) {
                dcssDesc.outcome.compareAndSet(null, true)
            } else {
                dcssDesc.outcome.compareAndSet(null, false)
            }

            if (dcssDesc.outcome.value == true) {
                cas(indexA, dcssDesc, updateA as Any)
                return true
            } else {
                cas(indexA, dcssDesc, expectedA as Any)
                return false
            }
        } else {
            return false
        }
    }

    private class CAS2Descriptor<E>(
        val indexA: Int, val expectedA: E, val updateA: E,
        val indexB: Int, val expectedB: E, val updateB: E
    ) {
        val outcome: AtomicRef<Boolean?> = atomic(null) // null = undecided, true = success, false = fail
    }

    private class DCSSDescriptor<E>(
        val indexA: Int, val expectedA: Any, val updateA: Any,
        val valB: Boolean?, val expectedB: Boolean?
    ) {
        val outcome: AtomicRef<Boolean?> = atomic(null) // null = undecided, true = success, false = fail
    }
}