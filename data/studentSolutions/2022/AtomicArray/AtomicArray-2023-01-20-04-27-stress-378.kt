import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            when (val it = a[index].value) {
                is AtomicArray<*>.DescriptorCASN -> it.complete()
                is AtomicArray<*>.DescriptorDCSS -> it.complete()
                else -> return it as E
            }
        }
    }

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        casD(index, expected, update)

    private fun casD(index: Int, expected: Any?, update: Any?): Boolean =
        a[index].compareAndSet(expected, update)


    private fun casR(index: Int, expected: E, update: Any): Boolean {
        a[index].loop {
            when (it) {
                is AtomicArray<*>.DescriptorCASN -> it.complete()
                is AtomicArray<*>.DescriptorDCSS -> it.complete()
                else -> {
                    if (it != expected) return@casR false
                    if (a[index].compareAndSet(it, update)) return@casR true
                }
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 > index2) return cas2(index2, expected2, update2, index1, expected1, update1)

        val d = DescriptorCASN(index1, expected1, update1, index2, expected2, update2)
        if (!casR(index1, expected1, d))
            return false

//        if (!d.outcomeCAS(Outcome.UNDECIDED, Outcome.PROCESS)) {
//            return when (d.outcome) {
//                Outcome.SUCCESS -> true
//                Outcome.FAIL -> false
//                else -> d.apply { complete() }.outcome == Outcome.SUCCESS
//            }
//        }
//        if (!cas(index2, expected2, d)) {
//            d.outcomeCAS(Outcome.PROCESS, Outcome.FAIL)
//            return when (d.outcome) {
//                Outcome.SUCCESS -> true
//                Outcome.FAIL -> false
//                else -> d.apply { complete() }.outcome == Outcome.SUCCESS
//            }
//        }
//        if (!d.outcomeCAS(Outcome.PROCESS, Outcome.SUCCESS)) {
//            return d.outcome == Outcome.SUCCESS
//        }
        d.complete()
        return d.outcome == Outcome.SUCCESS
    }

    private inner class DescriptorDCSS(
        val index: Int, val expectA: E, val updateA: Any,
        val casnOutcome: DescriptorCASN, val expectOutcome: Outcome
    ) {
        private val outcomeInner = atomic(Outcome.UNDECIDED)
        val outcome: Outcome
            get() = outcomeInner.value

        fun complete() {
            while (true) {
                when (outcomeInner.value) {
                    Outcome.UNDECIDED -> {
                        val v = if (casnOutcome.outcome == expectOutcome) Outcome.SUCCESS else Outcome.FAIL
                        outcomeInner.compareAndSet(Outcome.UNDECIDED, v)
                    }

                    Outcome.SUCCESS -> {
                        a[index].compareAndSet(this@DescriptorDCSS, updateA)
                        return
                    }

                    Outcome.FAIL -> {
                        a[index].compareAndSet(this@DescriptorDCSS, expectA)
                        return
                    }
                }
            }
        }
    }

    private inner class DescriptorCASN(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        private val outcomeInner = atomic(Outcome.UNDECIDED)
        val outcome: Outcome
            get() = outcomeInner.value

        fun outcomeCAS(expected: Outcome, update: Outcome) = outcomeInner.compareAndSet(expected, update)

        fun complete() {
            val d = this
            while (true) {
                when (outcomeInner.value) {
                    Outcome.UNDECIDED -> {
                        val d2 = DescriptorDCSS(index2, expected2, d, d, Outcome.UNDECIDED)
                        if (!a[index2].compareAndSet(expected2, d2)) {
                            d.outcomeInner.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                            continue
                        }
                        d2.complete()
                        if (d2.outcome == Outcome.FAIL) {
                            d.outcomeInner.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                            continue
                        }
                        a[index2].compareAndSet(d2, d)
                        d.outcomeInner.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                    }

                    Outcome.SUCCESS -> {
                        casD(index1, d, update1)
                        casD(index2, d, update2)
                        return
                    }

                    Outcome.FAIL -> {
                        casD(index1, d, expected1)
                        casD(index2, d, expected2)
                        return
                    }
                }
            }
        }
    }
}

private enum class Outcome { UNDECIDED, SUCCESS, FAIL }
