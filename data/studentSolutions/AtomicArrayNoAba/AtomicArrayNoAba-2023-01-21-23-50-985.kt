import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)
//    private var debug = false

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.value

    fun cas(index: Int, expected: E, update: E): Boolean {

        if (a[index] == null) return false
        val res = a[index]!!.cas(expected, update)
//        if (index == 4 && expected == 0 && update == 1) {
//            debug = true
//        }
        return res

    }


    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
//        if (index1 == 4 && expected1 == 0 && update1 == 1 && index2 == 2 && expected2 == 0 && update2 == 1 && debug) {
//            var dummy = index1 + 1
//            dummy -= 1
//        }
        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, update2) else false
        }
        var index1S = index1
        var index2S = index2
        var expected1S = expected1
        var expected2S = expected2
        var update1S = update1
        var update2S = update2
        if (index1 > index2) {
            index1S = index2.also { index2S = index1 }
            expected1S = expected2.also { expected2S = expected1 }
            update1S = update2.also { update2S = update1 }
        }
        val desc = CasnDescriptor(a[index1S]!!, expected1S, update1S, a[index2S]!!, expected2S, update2S)
        if (a[index1S]!!.cas(expected1S, desc)) {
            desc.complete()
            return desc.outcome.value == DescriptorOutcome.SUCCESS
        } else {
            return false
        }
    }


    private abstract class Descriptor {
        abstract fun complete()
    }

    private class Ref<E>(initial: E) {
        val v = atomic<Any?>(initial)

        var value: E
            get() {
                v.loop { cur ->
                    @Suppress("UNCHECKED_CAST")
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }

        fun cas(expected: Any?, update: Any?): Boolean {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return v.compareAndSet(expected, update)
                }
            }
        }
    }

    private class DCSSDescriptor<E>(
        val a: Ref<E>,
        val expectedA: E,
        val casnDescriptor: CasnDescriptor<E>
    ) : Descriptor() {
        val outcome = atomic(DescriptorOutcome.UNDECIDED)

        override fun complete() {
            if (casnDescriptor.outcome.value != DescriptorOutcome.UNDECIDED) {
                outcome.compareAndSet(DescriptorOutcome.UNDECIDED, DescriptorOutcome.FAILURE)
                a.v.compareAndSet(this, expectedA)
                return
            }
            outcome.compareAndSet(DescriptorOutcome.UNDECIDED, DescriptorOutcome.SUCCESS)
            a.v.compareAndSet(this, casnDescriptor)
        }
    }

    private class CasnDescriptor<E>(
        val a: Ref<E>,
        val expectedA: E,
        val updateA: E,
        val b: Ref<E>,
        val expectedB: E,
        val updateB: E
    ) : Descriptor() {
        val outcome = atomic(DescriptorOutcome.UNDECIDED)
        override fun complete() {
            if (dcss(b, expectedB, this)) {
                outcome.compareAndSet(DescriptorOutcome.UNDECIDED, DescriptorOutcome.SUCCESS)
            } else {
                if (b.v.value != this) {
                    outcome.compareAndSet(DescriptorOutcome.UNDECIDED, DescriptorOutcome.FAILURE)
                } else {
                    outcome.compareAndSet(DescriptorOutcome.UNDECIDED, DescriptorOutcome.SUCCESS)
                }
            }
            if (outcome.value == DescriptorOutcome.FAILURE) {
                a.v.compareAndSet(this, expectedA)
            } else {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            }
        }
    }

    private enum class DescriptorOutcome {
        UNDECIDED, SUCCESS, FAILURE
    }

    companion object {
        private fun <E> dcss(a: Ref<E>, expectedA: E, casnDescriptor: CasnDescriptor<E>): Boolean {
            val desc = DCSSDescriptor(a, expectedA, casnDescriptor)
            if (a.v.value != expectedA) {
                return false
            }
            if (a.cas(expectedA, desc)) {
                desc.complete()
                return desc.outcome.value == DescriptorOutcome.SUCCESS
            } else {
                return false
            }
        }
    }
}

