import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) {Ref(initialValue)}

//    init {
//        for (i in 0 until size) a[i].value = initialValue
//    }

    fun get(index: Int) =
        a[index].value

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val desc = CASNDescriptor(
            a[index1], expected1, update1,
            a[index2], expected2, update2,
            Outcome.UNDECIDED
        )
        val A = a[index1]
        A.v.compareAndSet(expected1, desc)
        val r = A.v.value
        if (r is CASNDescriptor<*, *>) {
            r.complete()
            return r.status.value == Outcome.SUCCESS
        } else {
            desc.status.value = Outcome.FAILED
            return false
        }
//        // TODO this implementation is not linearizable,
//        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
    }

//    private fun DCSS(
//        actualA: E, expectedA: E, updateA: E,
//        actualB: E, expectedB: E
//    ) {
//        val desc = DCSSDescriptor(
//            Ref(actualA), expectedA, updateA,
//            Ref(actualB), expectedB
//        )
//        val A = Ref(actualA)
//        A.v.compareAndSet(expectedA, desc)
//        val r = A.v.value
//        if (r is Descriptor) {
//            r.complete()
//        }
//    }

//    private fun modDCSS(
//        actualA: E, expectedA: E, updateA: E,
//        actualB: E, expectedB: E
//    ): Boolean {
//        val desc = ModDCSSDescriptor(
//            Ref(actualA), expectedA, updateA,
//            Ref(actualB), expectedB, Outcome.UNDECIDED
//        )
//        val A = Ref(actualA)
//        A.v.compareAndSet(expectedA, desc)
//        val r = A.v.value
//        if (r is ModDCSSDescriptor<*, *>) {
//            r.complete()
//            return r.status.value == Outcome.SUCCESS
//        } else {
//            desc.status.value = Outcome.FAILED
//            return false
//        }
//    }
}

private abstract class Descriptor {
    abstract fun complete() // Complete
}

private class CASNDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B,
    outcome: Outcome
) : Descriptor() {

    val status = atomic(outcome)
    override fun complete() {
        val desc = DCSSDescriptor(
            a, expectA, updateA,
            b, expectB
        )
        if (status.value != Outcome.UNDECIDED) {
            return
        }
        val (update, decision) = if (b.v.compareAndSet(expectB, desc))
            updateB to Outcome.SUCCESS else expectB to Outcome.FAILED
        status.compareAndSet(Outcome.UNDECIDED, decision)
        if (status.value == Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, update)
        } else {
            a.v.compareAndSet(this, expectA)
        }
    }
}

private class DCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B
) : Descriptor() {
    override fun complete() {
        val update = if (b.value === expectB)
            updateA else expectA
        a.v.compareAndSet(this, update)
    }
}

//private class ModDCSSDescriptor<A, B>(
//    val a: Ref<A>, val expectA: A, val updateA: A,
//    val b: Ref<B>, val expectB: B, outcome: Outcome
//) : Descriptor() {
//
//    val status = atomic(outcome)
//    override fun complete() {
//        val (update, decision) = if (b.value === expectB)
//            updateA to Outcome.SUCCESS else expectA to Outcome.FAILED
//        status.compareAndSet(Outcome.UNDECIDED, decision)
//        a.v.compareAndSet(this, update)
//    }
//}

private enum class Outcome {
    FAILED, UNDECIDED, SUCCESS
}

private class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)
    var value: T // RDCSSRead
        get()  {
            v.loop { cur ->
                when(cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
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