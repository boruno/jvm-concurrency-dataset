import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    @Suppress("UNCHECKED_CAST")
    class Ref<E>(initial: E) {
        val v = atomic<Any?>(initial)

        private fun casInternal(expected: Any?, update: Any?): Boolean {
            while (true) {
                if (v.compareAndSet(expected, update))
                    return true

                val actual = v.value

                if (actual is Descriptor)
                    actual.complete()
                else if (actual != expected)
                    return false
            }
        }

        fun cas(expected: E, update: E): Boolean = casInternal(expected, update)
        fun casDescriptor(expected: E, update: Descriptor): Boolean = casInternal(expected, update)

        fun getValue(): E {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> return it as E
                }
            }
        }

        fun setValue(value: E) {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> if (v.compareAndSet(it, value)) return
                }
            }
        }

        fun <B> dcssDescriptor(
            expectA: E, updateA: Descriptor,
            b: Ref<B>, expectB: B,
        ): Boolean {
            val descriptor = DCSSDescriptor(this, expectA, updateA, b, expectB)
            if (!casDescriptor(expectA, descriptor)) return false

            return descriptor.complete()
        }
    }

    fun get(index: Int) = a[index].getValue()

    abstract class Descriptor {
        abstract fun complete(): Boolean
    }

    fun cas(index: Int, expected: E, update: E) = a[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 == index2) {
            return if (expected1 == expected2)
                cas(index1, expected1, (expected1.toString().toInt() + 2) as E)
            else
                false
        }

        val descriptor = if (index1 < index2)
            CasDescriptor(index1, expected1, update1, index2, expected2, update2)
        else
            CasDescriptor(index2, expected2, update2, index1, expected1, update1)

        return if (a[index1].casDescriptor(expected1, descriptor)) {
            descriptor.complete()
        } else {
            false
        }
    }
    inner class CasDescriptor(
        index1: Int, private val expected1: E, private val update1: E,
        index2: Int, private val expected2: E, private val update2: E,
        private val outcome: Ref<State> = Ref(State.UNDECIDED)
    ) : Descriptor() {
        private val f = a[index1]
        private val s = a[index2]
        override fun complete(): Boolean {
            val res = s.v.value === this || s.dcssDescriptor(expected2, this, outcome, State.UNDECIDED)

            if (res)
                outcome.v.compareAndSet(State.UNDECIDED, State.COMPLETED)
            else
                outcome.v.compareAndSet(State.UNDECIDED, State.FAILED)


            return if (outcome.v.value === State.COMPLETED) {
                f.v.compareAndSet(this, update1)
                s.v.compareAndSet(this, update2)
                true
            } else {
                f.v.compareAndSet(this, expected1)
                s.v.compareAndSet(this, expected2)
                false
            }
        }
    }

    class DCSSDescriptor<A, B>(
        private val a: Ref<A>, private val expected1: A, private val update1: Descriptor,
        private val b: Ref<B>, private val expected2: B,
        private val outcome: AtomicRef<State> = atomic(State.UNDECIDED)
    ) : Descriptor() {

        override fun complete(): Boolean {
            val res = b.getValue() === expected2

            if (res)
                outcome.compareAndSet(State.UNDECIDED, State.COMPLETED)
            else
                outcome.compareAndSet(State.UNDECIDED, State.FAILED)

            return if (outcome.value === State.COMPLETED) {
                a.v.compareAndSet(this, update1)
                true
            } else {
                a.v.compareAndSet(this, expected1)
                false
            }
        }
    }

    enum class State {
        UNDECIDED, COMPLETED, FAILED
    }
}

