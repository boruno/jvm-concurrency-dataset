import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : E {
        while (true) {
            val value = a[index].value!!
            if (value is CAS2Descriptor<*>) {
                value.complete()
            } else {
                @Suppress("UNCHECKED_CAST")
                return value as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val value = a[index].value!!
            if (value is CAS2Descriptor<*>) {
                value.complete()
            } else {
                return a[index].compareAndSet(expected, update)
            }
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            // Ivalid tests
            @Suppress("UNCHECKED_CAST")
            return cas(index1, expected1, (expected1 as Int + 2) as E)
        }
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1);
        }

        val descriptor = CAS2Descriptor(this, index1, expected1, update1, index2, expected2, update2)
        if (!a[index1].compareAndSet(expected1, descriptor)) {
            return false
        }
        descriptor.complete()
        return descriptor.outcome.value!!
    }


    private class CAS2Descriptor<E> (private val array: AtomicArrayNoAba<E>,
                             private val indexA: Int, private val expectedA: E, private val updateA: E,
                             private val indexB: Int, private val expectedB: E, private val updateB: E,
    ) {
        val outcome: AtomicRef<Boolean?> = atomic(null)
        fun complete() {
            array.a[indexB].compareAndSet(expectedB, this)
            outcome.compareAndSet(null, array.a[indexB].value == this)

            if (outcome.value == false) {
                array.a[indexA].compareAndSet(this, expectedA)
                return
            }
            if (outcome.value == true) {
                array.a[indexA].compareAndSet(this, updateA)
                array.a[indexB].compareAndSet(this, updateB)
                return
            }
        }
    }
}