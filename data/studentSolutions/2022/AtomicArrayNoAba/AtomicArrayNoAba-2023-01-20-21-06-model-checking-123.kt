import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Reference<Any?>>(size)

    init {
        for (i in 0 until size) a[i].value = Reference(initialValue)
    }

    fun get(index: Int): E = a[index].value!!.getValue() as E

    fun cas(index: Int, expected: E, update: E): Boolean = a[index].value!!.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {
        var first: SwapDescription<E>? = null
        var second: SwapDescription<E>? = null
        if (index1 < index2) {
            first = SwapDescription(a[index1].value!!, expected1, update1)
            second = SwapDescription(a[index2].value!!, expected2, update2)
        } else if (index1 > index2) {
            first = SwapDescription(a[index2].value!!, expected2, update2)
            second = SwapDescription(a[index1].value!!, expected1, update1)
        } else {
            throw AssertionError("wtf in cas2")
        }

        val desc = casnDescriptor(
            first.actual1, first.expected, first.update, second.actual1, second.expected, second.update
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
        val actual1: Reference<Any?>,
        val expected1: E,
        val update1: E,
        val actual2: Reference<Any?>,
        val expected2: E,
        val update2: E
    ) {
        val outcome = Reference(Res.undecided as Any?)
        fun complete() {
            val dcss = dcssDescriptor(actual2, expected2, this, outcome, Res.undecided)
            if (dcss.dcssTwoIndexes(actual2, expected2, this, outcome, Res.undecided)) {
                if (outcome.compareAndSet(Res.undecided, Res.success)) {
                    if (!actual1.compareAndSet(this, update1)) {
                        throw AssertionError("actual1")
                    }
                    if (!actual2.compareAndSet(this, update2)) {
                        throw AssertionError("actual2")
                    }
                }
            } else {
                if (outcome.compareAndSet(Res.undecided, Res.nots)) {
                    actual1.compareAndSet(this, expected1)
                }
            }
        }
    }

    enum class Res {
        undecided, success, nots,
    }

    private class dcssDescriptor<E1, E2>(
        val actual1: Reference<Any?>,
        val expected1: E1,
        val update1: E1,
        val actual2: Reference<Any?>,
        val expected2: E2
    ) {
        val outcome = atomic(Res.undecided)

        public fun <E1, E2> dcssTwoIndexes(
            actual1: Reference<Any?>, expected1: E1, update1: E1, actual2: Reference<Any?>, expected2: E2
        ): Boolean {// если вызвал на ячейке в которой стоит casn дескриптор
            // TODO: посортить по index'ам
            val descriptor = dcssDescriptor(actual1, expected1, update1, actual2, expected2)
            if (!actual1.compareAndSet(expected1, descriptor)) {
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

        fun complete() {
//            val update = if (actual2.getValue() == expected2) update1 else expected1
            if (actual2.getValue() == expected2) {
                if (outcome.compareAndSet(Res.undecided, Res.success)) {
                    if (!actual1.compareAndSet(this, update1)) {
                        throw AssertionError("dcss compelete cas")
                    }
                }
            } else {
                if (outcome.compareAndSet(Res.undecided, Res.nots)) {
                    if (!actual1.compareAndSet(this, expected1)) {
                        throw AssertionError("dcss compelete cas2")
                    }
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
                    is casnDescriptor<*> -> cur.complete()
                    else -> return cur as T
                }
            }
        }

        fun setValue(upd: T) {
            v.loop { cur ->
                when (cur) {
                    is dcssDescriptor<*, *> -> cur.complete()
                    is casnDescriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

        fun compareAndSet(expected: T, upd: T): Boolean {
            return v.compareAndSet(expected, upd)
        }
    }
}