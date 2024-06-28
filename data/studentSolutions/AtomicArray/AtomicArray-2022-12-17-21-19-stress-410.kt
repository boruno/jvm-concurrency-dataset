import kotlinx.atomicfu.*

enum class OutcomeStatus {
    UNDECIDE, SUCCESS, FAIL
}


interface Descriptor {
    fun complete()
}

class Ref<E>(initialValue: E) {
    private val v = atomic<Any?>(initialValue)

    @Suppress("UNCHECKED_CAST")
    var value: E
        get() {
            while (true) {
                when (val cur = v.value) {
                    is Descriptor -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(value) {
            while (true) {
                when (val cur = v.value) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, value)) return
                }
            }
        }
    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            val cur = v.value
            if (cur == expected) {
                val res = v.compareAndSet(cur, update)
                if (res) {
                    return true
                }
            } else return false
        }
    }
}

class RDCSSDescriptor<E, T>(
    private val a: Ref<E>, private val expectA: E, private val updateA: Any?,
    private val b: Ref<T>, private val expectB: T
) : Descriptor {
    val outcome: Ref<OutcomeStatus> = Ref(OutcomeStatus.UNDECIDE)

    override fun complete() {
        //println("complete for RDCSSDescriptor: $a $expectA $updateA $b $expectB")
        val curOutcome = outcome.value
        if (curOutcome == OutcomeStatus.UNDECIDE) {
            if (b.value == expectB) {
                outcome.cas(OutcomeStatus.UNDECIDE, OutcomeStatus.SUCCESS)
            } else {
                outcome.cas(OutcomeStatus.UNDECIDE, OutcomeStatus.FAIL)
            }
        }
        if (outcome.value == OutcomeStatus.SUCCESS) {
            a.cas(this, updateA)
        } else {
            a.cas(this, expectA)
        }
    }
}

class CASNDescriptor<E, T>(
    private val a: Ref<E>, private val expectA: E, private val updateA: E,
    private val b: Ref<T>, private val expectB: T, private val updateB: T
) : Descriptor {
    val outcome: Ref<OutcomeStatus> = Ref(OutcomeStatus.UNDECIDE)

    override fun complete() {
        //println("complete for CASNDescriptor: $a $expectA $updateA $b $expectB $updateB")
        // println("current outcome: ${outcome.value}")
        val dcss = RDCSSDescriptor(b, expectB, this, outcome, OutcomeStatus.UNDECIDE)
        if (outcome.value == OutcomeStatus.UNDECIDE) {
            if (b.cas(expectB, dcss)) {
                //println("b changed to descriptor")
                dcss.complete()
                if (dcss.outcome.value == OutcomeStatus.SUCCESS) {
                    //println("b changed to update value")
                    outcome.cas(OutcomeStatus.UNDECIDE, OutcomeStatus.SUCCESS)
                } else {
                    outcome.cas(OutcomeStatus.UNDECIDE, OutcomeStatus.FAIL)
                    //println("b fail changed to update value")
                    a.cas(this, expectA)
                    return
                }
            } else {
                outcome.cas(OutcomeStatus.UNDECIDE, OutcomeStatus.FAIL)
            }
        }
        if (outcome.value == OutcomeStatus.SUCCESS) {
            a.cas(this, updateA)
            b.cas(this, updateB)
        } else {
            a.cas(this, expectA)
            b.cas(this, expectB)
        }
    }
}

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        (0 until size).forEach { i ->
            a[i] = Ref(initialValue)
        }
    }

    fun get(index: Int): E = a[index]!!.value

    fun set(index: Int, value: E) {
        a[index]?.value = value
    }

    fun cas(index: Int, expected: E, update: E): Boolean = a[index]?.cas(expected, update) ?: false

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        //println("cas2 for $index1 $index2")
        return if (index1 == index2) {
            if (expected1 == expected2) cas(index1, expected1, update2) else false
        } else if (index1 > index2) {
            val descriptor = CASNDescriptor(a[index2]!!, expected2, update2, a[index1]!!, expected1, update1)
            if (a[index2]!!.cas(expected2, descriptor)) {
                //println("a changed")
                descriptor.complete()
                descriptor.outcome.value == OutcomeStatus.SUCCESS
            } else {
                false
            }
        } else {
            val descriptor = CASNDescriptor(a[index1]!!, expected1, update1, a[index2]!!, expected2, update2)
            if (a[index1]!!.cas(expected1, descriptor)) {
                descriptor.complete()
                descriptor.outcome.value == OutcomeStatus.SUCCESS
            } else {
                false
            }
        }
    }
}