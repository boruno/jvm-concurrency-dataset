import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Reference<E>>(size)

    init {
        for (i in 0 until size) {
            array[i].value = Reference(initialValue)
        }
    }

    fun get(index: Int): E =
        array[index].value!!.value

    fun cas(index: Int, expected: E, update: E): Boolean =
        array[index].value!!.update(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        return if (index1 == index2) {
            false
        } else if (index1 < index2) {
            complete(index1, expected1, CAS2Descriptor(array[index1].value!!, expected1, update1,
                array[index2].value!!, expected2, update2))
        } else {
            complete(index2, expected2, CAS2Descriptor(array[index2].value!!, expected2, update2,
                array[index1].value!!, expected1, update1))
        }
    }

    private fun complete(index: Int, expected: Any?, descriptor: CAS2Descriptor<E>): Boolean {
        return if (array[index].value!!.update(expected, descriptor)) {
            descriptor.complete()
            descriptor.getOutcome() == Outcome.SUCCESS
        } else {
            false
        }
    }
}

class Reference<E>(initialValue: E) {
    val v = atomic<Any?>(initialValue)

    @Suppress("UNCHECKED_CAST")
    var value: E
        get() {
            v.loop { cur ->
                when (cur) {
                    is CAS2Descriptor<*> -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is CAS2Descriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) {
                        return
                    }
                }
            }
        }

    fun update(expected: Any?, update: Any?): Boolean {
        v.loop { cur ->
            when (cur) {
                is CAS2Descriptor<*> -> cur.complete()
                else -> return v.compareAndSet(expected, update)
            }
        }
    }
}

class CAS2Descriptor<E>(
    private val a: Reference<E>, private val expected1: E, private val update1: E,
    private val b: Reference<E>, private val expected2: E, private val update2: E
) {
    private val outcome: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)

    fun getOutcome(): Outcome {
        return outcome.value
    }

    fun complete() {
        if (b.v.value == this || b.update(expected2, this)) {
            this.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        } else {
            this.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
        }
        if (this.outcome.value == Outcome.SUCCESS) {
            a.v.compareAndSet(this, update1)
            b.v.compareAndSet(this, update2)
        } else {
            a.v.compareAndSet(this, expected1)
            b.v.compareAndSet(this, expected2)
        }
    }
}

    enum class Outcome {
        UNDECIDED, SUCCESS, FAIL
    }