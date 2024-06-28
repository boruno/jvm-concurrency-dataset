import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = Array(size){Ref<E>(initialValue)}

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }


    fun cas(index: Int, expected: E, update: E) =
        a[index].casOrHelp(expected, update)

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {
        if (indexA == indexB) {
            return if (expectedA == expectedB) {
                cas(indexA, expectedA, expectedA)
            } else false
        }

        val refA = a[indexA]
        val refB = a[indexB]
        val descriptor: CAS2Descriptor
        if (indexA > indexB) {
            descriptor = CAS2Descriptor(refA, expectedA, updateA, refB, expectedB, updateB)
            if (!refA.casOrHelp(expectedA, descriptor)) return false
        } else {
            descriptor = CAS2Descriptor(refB, expectedB, updateB, refA, expectedA, updateA)
            if (!refB.casOrHelp(expectedB, descriptor)) return false
        }
        descriptor.complete()

        return descriptor.outcome.value == Outcome.SUCCESS
    }

    private abstract inner class Descriptor {
        val outcome = Ref<Outcome>(Outcome.UNDECIDED)
        abstract fun complete()
    }

    class Ref<T>(inital: Any?) {
        val v = atomic(inital)

        var value: T
            get() {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArray<*>.Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }

        fun casOrHelp(expectA: Any?, updateA: Any?): Boolean {
            while (true) {
                if (v.compareAndSet(expectA, updateA)) return true
                val aVal = v.value
                if (aVal is AtomicArray<*>.Descriptor) {
                    aVal.complete()
                    continue
                }
                if (aVal != expectA) {
                    return false
                }
            }
        }
    }

    private inner class DCSSDescriptor(
        val a: Ref<E>, val expectA: Any?, val updateA: Any?,
        val b: Ref<Outcome>, val expectB: Any?
    ) : Descriptor() {
        override fun complete() {
            val update = if (b.value == expectB) {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                updateA
            } else {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                expectA
            }

            a.v.compareAndSet(this, update)
        }
    }

    fun dcss(
        a: Ref<E>, expectA: Any?, updateA: Any?,
        b: Ref<Outcome>, expectB: Any?): Boolean {
        val descriptor = DCSSDescriptor(a, expectA, updateA, b, expectB)
//    either change or help to change
        if (a.casOrHelp(expectA, descriptor)) {
            descriptor.complete()
            return descriptor.outcome.value == Outcome.SUCCESS
        }
        descriptor.outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
        return false

    }

    private inner class CAS2Descriptor(
        val a: Ref<E>, val expectA: Any?, val updateA: Any?,
        val b: Ref<E>, val expectB: Any?, val updateB: Any?
    ) : Descriptor() {
        override fun complete() {
            val (valA, valB) = if (b.v.value == this || dcss(b, expectB, this,
                    outcome, Outcome.UNDECIDED)) {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                Pair(updateA, updateB)
            } else {
                outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                Pair(expectA, expectB)
            }

            a.v.compareAndSet(this, valA)
            b.v.compareAndSet(this, valB)
        }
    }

    enum class Outcome { UNDECIDED, SUCCESS, FAIL }
}