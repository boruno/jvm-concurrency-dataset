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

        val cas2Descriptor = CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)

        if (a[index1].value!!.cas(expected1 as Any, cas2Descriptor)) {
            if (a[index2].value!!.cas(expected2 as Any, cas2Descriptor)) {
                cas2Descriptor.outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
                cas2Descriptor.complete()
                return true
            }
        }

        return false
    }
    inner class CAS2Descriptor<E>(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E,
    ): Descriptor {
        val outcome: AtomicRef<OUTCOME> = atomic(OUTCOME.UNDECIDED)
        override fun complete() {
//            if (outcome.value == OUTCOME.UNDECIDED) {
//                if (a[index2].value!!.cas(expected2 as Any, this)) {
//                    outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.SUCCESS)
//                } else {
//                    outcome.compareAndSet(OUTCOME.UNDECIDED, OUTCOME.FAILED)
//                }
//            }

            if (outcome.value == OUTCOME.SUCCESS) {
                a[index1].value!!.cas(this, expected1 as Any)
                a[index2].value!!.cas(this, expected2 as Any)
            }

            if (outcome.value == OUTCOME.FAILED) {
                a[index1].value!!.cas(this, expected1 as Any)
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
        while (true) {
            val val1 = _value.value

            when (val1) {
                is AtomicArrayNoAba<*>.CAS2Descriptor<*> -> val1.complete()
                else -> {
                    if (val1 == expected) {
                        if (_value.compareAndSet(expected, update)) {
                            return true
                        }
                    } else {
                        return false
                    }
                }
            }
        }
    }
}