import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            when (val it = a[index].value) {
                is AtomicArrayNoAba<*>.DCASN -> it.complete()
                else -> return it as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean =
        casD(index, expected, update)

    private fun casD(index: Int, expected: Any, update: Any): Boolean =
        a[index].compareAndSet(expected, update)


    private fun casR(index: Int, expected: E, update: Any): Boolean {
        a[index].loop {
            when (it) {
                is AtomicArrayNoAba<*>.DCASN -> it.complete()
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

        val d = DCASN(index1, expected1, update1, index2, expected2, update2)
        if (!casR(index1, expected1, d))
            return false

        d.complete()
        return d.outcome == Outcome.SUCCESS
    }

    private inner class DCASN(
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
                        val r = if (casD(index2, expected2, d)) Outcome.SUCCESS else Outcome.FAIL
                        d.outcomeInner.compareAndSet(Outcome.UNDECIDED, r)
                    }

                    Outcome.SUCCESS -> {
                        casD(index1, d, update1)
                        casD(index2, d, update2)
                        return
                    }

                    Outcome.FAIL -> {
                        casD(index1, d, expected1)
//                        casD(index2, d, expected2)
                        return
                    }
                }
            }
        }
    }

}

private enum class Outcome { UNDECIDED, SUCCESS, FAIL }
