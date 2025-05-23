import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Element<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Element(initialValue)
    }

    fun get(index: Int): E = a[index].value!!.value

    fun cas(index: Int, expected: E, update: E): Boolean {
        return a[index].value!!.cas(expected as Any, update as Any)
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        if (index1 == index2) {
            a[index1].value!!.cas(expected1 as Any, (update1 as Int) + (update2 as Int))
        }

        if (index1 < index2) {
            val cas2Descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
            return if (a[index1].value!!.cas(expected1 as Any, cas2Descriptor)) {
                cas2Descriptor.complete()
                cas2Descriptor.outcome.value == OUTCOME.SUCCESS
            } else {
                false
            }
        } else {
            val cas2Descriptor = CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
            return if (a[index2].value!!.cas(expected2 as Any, cas2Descriptor)) {
                cas2Descriptor.complete()
                cas2Descriptor.outcome.value == OUTCOME.SUCCESS
            } else {
                false
            }
        }
    }

    inner class CAS2Descriptor<E>(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E,
    ): Descriptor {
        val outcome: AtomicRef<OUTCOME> = atomic(OUTCOME.UNDECIDED)
        override fun complete() {
            if (outcome.value == OUTCOME.UNDECIDED && a[index2].value!!.cas(expected2 as Any, this)) {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
            } else {
                outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
            }

            if (outcome.value == OUTCOME.SUCCESS) {
                a[index1].value!!._value.compareAndSet(this, update1)
                a[index2].value!!._value.compareAndSet(this, update2)
            } else {
                a[index1].value!!._value.compareAndSet(this, expected1)
                a[index2].value!!._value.compareAndSet(this, expected2)
            }
        }
    }
}

interface Descriptor {
    fun complete()
}

enum class OUTCOME {
    SUCCESS, UNDECIDED, FAILED
}
class Element<E>(private val initValue: E) {
    val _value = atomic<Any?>(initValue)

    var value: E
        get() {
            while (true) {
                when (val elem = _value.value) {
                    is AtomicArrayNoAba<*>.CAS2Descriptor<*> -> elem.complete()
                    else -> return elem as E
                }
            }
        }
        set(value) { _value.value = value }

    fun cas(expected: Any, update: Any): Boolean {
        return _value.compareAndSet(expected, update)
    }
}