import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int): E = a[index].value

    fun cas(index: Int, expected: E, update: E): Boolean = a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean =
        if (index1 == index2) {
            require(expected1 === expected2) { "expected1 should be equal to expected2" }
            require(update1 === update2) { "update1 should be equal to update2" }

            // На английском «
            @Suppress("UNCHECKED_CAST")
            cas(index1, expected1, (update1 as Int + 1) as E)
        } else {
            createCASNDescriptor(index1, expected1, update1, index2, expected2, update2).execute()
        }

    private fun createCASNDescriptor(
        index1: Int, expected1: Any?, update1: Any?,
        index2: Int, expected2: Any?, update2: Any?
    ): CASNDescriptor =
        if (index1 < index2) {
            CASNDescriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CASNDescriptor(index2, expected2, update2, index1, expected1, update1)
        }

    private class Ref<E>(initialValue: E) {
        private val v = atomic<Any?>(initialValue)

        var value: E
            get() = loop { @Suppress("UNCHECKED_CAST") return (it as E) }
            set(value) = loop { if (v.compareAndSet(it, value)) return }

        val rawValue: Any?
            get() = v.value

        fun cas(expected: Any?, update: Any?): Boolean = v.loop {
            when (it) {
                is AtomicArrayNoAba<*>.Descriptor -> it.complete()
                else -> return if (it === expected) v.compareAndSet(it, update) else false
            }
        }

        fun compareAndSet(expected: Any?, update: Any?): Boolean = v.compareAndSet(expected, update)

        inline fun loop(block: (Any?) -> Unit): Nothing {
            v.loop {
                when (it) {
                    is AtomicArrayNoAba<*>.Descriptor -> it.complete()
                    else -> block(it)
                }
            }
        }
    }

    private abstract inner class Descriptor(val index: Int, val expected: Any?) {
        private val state = atomic(State.IN_PROGRESS)

        private val completed
            get() = state.value != State.IN_PROGRESS

        private val success
            get() = state.value == State.SUCCESS

        fun execute(): Boolean {
            assert(!completed) { "Already completed" }
            return a[index].compareAndSet(expected, this) && complete()
        }

        fun complete(): Boolean {
            assert(a[index].rawValue === this || state.value != State.IN_PROGRESS) { "Invalid state" }

            if (state.value == State.IN_PROGRESS) {
                val newResult = if (decide()) State.SUCCESS else State.FAILURE
                state.compareAndSet(State.IN_PROGRESS, newResult)
            }

            return when (state.value) {
                State.SUCCESS -> {
                    completeSuccess()
                    true
                }

                State.FAILURE -> {
                    completeFailure()
                    false
                }

                else -> error("Inconsistent state")
            }
        }

        protected abstract fun decide(): Boolean
        protected abstract fun completeFailure()
        protected abstract fun completeSuccess()
    }

    private inner class CASNDescriptor(
        private val index1: Int, private val expected1: Any?, private val update1: Any?,
        private val index2: Int, private val expected2: Any?, private val update2: Any?
    ) : Descriptor(index1, expected1) {
        init {
            require(index1 < index2) { "index1 must be less than index2 to avoid deadlock" }
        }

        override fun decide(): Boolean = a[index2].compareAndSet(expected2, this)

        override fun completeFailure() {
            a[index1].compareAndSet(this, expected1)
        }

        override fun completeSuccess() {
            a[index1].compareAndSet(this, update1)
            a[index2].compareAndSet(this, update2)
        }
    }

    private enum class State { IN_PROGRESS, SUCCESS, FAILURE }
}
