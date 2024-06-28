import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Reference<Any?>>(size)

    init {
        for (i in 0 until size) a[i].value = Reference(initialValue)
    }

        fun get(index: Int): E =
        a[index].value!!.getValue() as E

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.compareAndSet(expected, update)
//
    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        val first: SwapDescription<E>? = null
        val second: SwapDescription<E>? = null
        if (index1 < index2) {
            val first = SwapDescription(a[index1].value!!, expected1, update1)
            val second = SwapDescription(a[index2].value!!, expected2, update2)
        } else if (index1 > index2) {
            val first = SwapDescription(a[index2].value!!, expected2, update2)
            val second = SwapDescription(a[index1].value!!, expected1, update1)
        } else {
            throw AssertionError("wtf in cas2")
        }

        val desc = casnDescriptor(
            first!!.actual1,
            first!!.expected,
            first!!.update,
            second!!.actual1,
            second!!.expected,
            second!!.update
        )
        if (!first.actual1.compareAndSet(first.expected, desc)) {
            return false
        }
        desc.complete()
        if (desc.outcome.getValue() == Res.success) {
            return true
        } else if (desc.outcome.getValue() == Res.nots) {
            return false
        } else {
            throw AssertionError("wtf2 in cas2")
        }
    }

    private class SwapDescription<E>(val actual1: Reference<Any?>, val expected: E, val update: E)

    private class casnDescriptor<E>(
        val actual1: Reference<Any?>, val expected1: E, val update1: E,
        val actual2: Reference<Any?>, val expected2: E, val update2: E
    ) {
        val outcome = Reference(Res.undecided as Any?)
        fun complete() {
            val dcss = dcssDescriptor(actual2, expected2, this, outcome, Res.undecided)
            dcss.complete()
            if (dcss.outcome.value == Res.success) {
                // нужно обновлять значения и выставить свой outcome
                if (outcome.compareAndSet(Res.undecided, Res.success)) {
                    actual1.compareAndSet(this, expected1) // можно добавить ассерт
                    actual2.compareAndSet(this, expected2)
                }
            } else {
                if (outcome.compareAndSet(Res.undecided, Res.nots)) {
                    actual1.compareAndSet(this, expected1)
                }
            }
        }
    }

    private fun dcssTwoIndexes(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E
    ): Boolean {
        // TODO: посортить по index'ам
        val descriptor = dcssDescriptor(a[index1].value!!, expected1, update1, a[index2].value!!, expected2)
        if (!a[index1].value!!.compareAndSet(expected1, descriptor)) {
            return false
        }
        descriptor.complete()
        if (descriptor.outcome.value == Res.success) {
            return true
        } else if (descriptor.outcome.value == Res.nots) {
            return false
        } else {
            throw AssertionError("wtf in dcss")
        }
    }

    enum class Res {
        undecided,
        success,
        nots,
    }

    private class dcssDescriptor<E1, E2>(
        val actual1: Reference<Any?>, val expected1: E1, val update1: E1,
        val actual2: Reference<Any?>, val expected2: E2
    ) {
        val outcome = atomic(Res.undecided)

        fun complete() {
//            val update = if (actual2.getValue() == expected2) update1 else expected1
            if (actual2.getValue() == expected2) {
                if (outcome.compareAndSet(Res.undecided, Res.success)) {
                    actual1.compareAndSet(this, update1)
                }
            } else {
                if (outcome.compareAndSet(Res.undecided, Res.nots)) {
                    actual1.compareAndSet(this, expected1)
                }
            }
        }
    }

    private class Reference<T>(initial: T) {
        val v = atomic<Any?>(initial)

        fun getValue(): T {
            v.loop { cur ->
                when (cur) {
                    is dcssDescriptor<*, *> -> cur.complete()
                    else -> return cur as T
                }
            }
        }

        fun setValue(upd: T) {
            v.loop { cur ->
                when (cur) {
                    is dcssDescriptor<*, *> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd))
                        return
                }
            }
        }

        fun compareAndSet(expected: T, upd: T): Boolean {
            return v.compareAndSet(expected, upd)
        }
    }
}