import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        var elem = a[index].value
        while (elem is CASDescriptor<*>) {
            descTransition(elem)
            elem = a[index].value
        }
        return elem as E
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        var elem = a[index].value
        while (elem is CASDescriptor<*>) {
            descTransition(elem)
            elem = a[index].value
        }
        return a[index].compareAndSet(expected, update)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 > index2) return cas2(index2, expected2, update2, index1, expected1, update1)
        if (index1 == index2) return cas(index1, expected1, update2)
        val desc = CASDescriptor(index1, expected1, update1, index2, expected2, update2)
        if (!a[index1].compareAndSet(expected1, desc))
            return false
        descTransition(desc)
        if (a[index2].value is CASDescriptor<*>)
            descTransition(desc)
        return desc.outcome.value == Outcome.SUCCESS
    }

    private fun descTransition(desc: CASDescriptor<*>) {
        when(desc.outcome.value) {
            Outcome.UNDECIDED -> {
                if (a[desc.index2].compareAndSet(desc.expected2, desc))
                    desc.success()
                else
                    desc.fail()
            }
            Outcome.SUCCESS -> {
                a[desc.index1].compareAndSet(desc, desc.update1)
                a[desc.index2].compareAndSet(desc, desc.update2)
            }
            Outcome.FAIL -> {
                a[desc.index1].compareAndSet(desc, desc.expected1)
                a[desc.index2].compareAndSet(desc, desc.expected2)
            }
        }
    }

    class CASDescriptor<E>(val index1: Int, val expected1: E, val update1: E,
                        val index2: Int, val expected2: E, val update2: E) {
        val outcome = atomic(Outcome.UNDECIDED)
        fun success() {
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        }
        fun fail() {
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
        }
    }


    enum class Outcome {
        UNDECIDED, SUCCESS, FAIL
    }

}