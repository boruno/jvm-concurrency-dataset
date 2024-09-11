import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E = a[index].loop {
        when (it) {
            is AtomicArrayNoAba<*>.Descriptor -> it.complete()
            else -> return it as E
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean = a[index].loop {
        when (it) {
            is AtomicArrayNoAba<*>.Descriptor -> it.complete()
            else -> return a[index].compareAndSet(expected, update)
        }
    }

    fun cas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean =
        createCASNDescriptor(index1, expected1, update1, index2, expected2, update2).execute()

    private fun createCASNDescriptor(
        index1: Int, expected1: Any?, update1: Any?,
        index2: Int, expected2: Any?, update2: Any?
    ): CASNDescriptor =
        if (index1 < index2) {
            CASNDescriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CASNDescriptor(index2, expected2, update2, index1, expected1, update1)
        }

    private abstract inner class Descriptor(val index: Int, val expected: Any?) {
        private val state = atomic(State.IN_PROGRESS)

        private val completed
            get() = state.value != State.IN_PROGRESS

        private val success
            get() = state.value == State.SUCCESS

        fun execute(): Boolean {
            val index = index

            a[index].loop {
                when {
                    completed -> return success
                    it is AtomicArrayNoAba<*>.Descriptor -> it.complete()
                    else -> return a[index].compareAndSet(expected, this) && complete()
                }
            }
        }

        fun complete(): Boolean {
            if (state.value == State.IN_PROGRESS) {
                val newResult = if (decide()) State.SUCCESS else State.FAILURE
                state.compareAndSet(State.IN_PROGRESS, newResult)
            }

            assert(state.value != State.IN_PROGRESS) { "Inconsistent state" }

            return if (state.value == State.SUCCESS) {
                completeSuccess()
                true
            } else {
                completeFailure()
                false
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
            require(index1 < index2) { "index1 must be less than index2 to avoid ABA problem" }
        }

        private val dcssDescriptor = DCSSDescriptor(index2, expected2, this, index1, this)

        override fun decide(): Boolean = dcssDescriptor.execute()

        override fun completeFailure() {
            a[index1].compareAndSet(this, expected1)
            a[index2].compareAndSet(this, expected2)
        }

        override fun completeSuccess() {
            a[index1].compareAndSet(this, update1)
            a[index2].compareAndSet(this, update2)
        }
    }

    private inner class DCSSDescriptor(
        private val index1: Int, private val expected1: Any?, private val update: Any?,
        private val index2: Int, private val expected2: Any?
    ) : Descriptor(index1, expected1) {
        override fun decide(): Boolean = a[index2].compareAndSet(expected2, update)

        override fun completeFailure() {
            a[index1].compareAndSet(this, expected1)
        }

        override fun completeSuccess() {
            a[index1].compareAndSet(this, update)
        }
    }

    private enum class State { IN_PROGRESS, SUCCESS, FAILURE }
}
