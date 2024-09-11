import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = mutableListOf<Ref>()

    init {
        for (i in 0 until size) a.add(Ref(initialValue))
    }

    fun get(index: Int): E = a[index].value as E

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        val curValue = get(index)
        println("cas $index $expected $update $curValue")
        return curValue == expected && a[index].v.compareAndSet(curValue, update)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        println("cas2 $index1 $expected1 $update1 $index2 $expected2 $update2 ${a[index1].value} ${a[index2].value}")
        if (index1 == index2) {
            if (expected1 != expected2 || update1 != update2 || update1 !is Int) {
                return false
            }
            return cas(index1, expected1, (update1 + 1) as E)
        }
        val (indexA, indexB) = if (index1 < index2) Pair(index1, index2) else Pair(index2, index1)
        val (expectedA, expectedB) = if (index1 < index2) Pair(expected1, expected2) else Pair(expected2, expected1)
        val (updateA, updateB) = if (index1 < index2) Pair(update1, update2) else Pair(update2, update1)
        val descriptor = CASNDescriptor(
            a[indexA], expectedA, updateA,
            a[indexB], expectedB, updateB
        )
        val curA = get(indexA)
        if (curA != expectedA || !a[indexA].v.compareAndSet(curA, descriptor)) {
            return false
        }
        return descriptor.complete()
    }

    private class CASNDescriptor(
        val a: Ref,
        val expectedA: Any?,
        val updateA: Any?,
        val b: Ref,
        val expectedB: Any?,
        val updateB: Any?,
    ) : Descriptor {
        val outcome = Ref(Outcome.UNDECIDED)

        override fun complete(): Boolean {
            println("Complete casn")
            val descriptor = RDCSSDescriptor(b, expectedB, this, outcome, Outcome.UNDECIDED)
            if (!b.v.compareAndSet(expectedB, descriptor) || !descriptor.complete()) {
                val newB = b.value
                if (newB != this) {
                    outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                }
            }
            outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            if (outcome.value == Outcome.SUCCESS) {
                b.v.compareAndSet(this, updateB)
                a.v.compareAndSet(this, updateA)
            } else {
                b.v.compareAndSet(this, expectedB)
                a.v.compareAndSet(this, expectedA)
            }

            return outcome.value == Outcome.SUCCESS
        }
    }

    private enum class Outcome {
        UNDECIDED,
        SUCCESS,
        FAIL,
    }

    class Ref(initial: Any?) {
        val v = atomic(initial)

        var value: Any?
            get() {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur!!
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    interface Descriptor {
        fun complete(): Boolean
    }

    private class RDCSSDescriptor(
        val a: Ref, val expectA: Any?, val updateA: Any?,
        val b: Ref, val expectB: Any?
    ) : Descriptor {
        val outcome = Ref(Outcome.UNDECIDED)

        override fun complete(): Boolean {
            if (b.value === expectB) {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            } else {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            }
            val update = if (outcome.value == Outcome.SUCCESS)
                updateA else expectA
            a.v.compareAndSet(this, update)

            return outcome.value == Outcome.SUCCESS
        }
    }

}