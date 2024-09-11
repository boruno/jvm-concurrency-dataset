import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.getValue()

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.casValue(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val desc = Desc(a[index1].value!!, expected1, update1,
                                a[index2].value!!, expected2, update2)
        val ref1 = a[index1].value!!
        val ref2 = a[index2].value!!
        if (!ref1.casValue(ref1, desc)) return false
        if (desc.outcome.value == DescOutcome.UNDECIDED) {
            val outcome = dcss(index2, expected2, update2, index1, expected1)
            if (outcome) {
                ref1.setValue(update1)
                ref2.setValue(update2)
                return true
            }
            return false
        }
        return false
    }

    private fun dcss(index1: Int, expected1: E, update1: E,
                     index2: Int, expected2: E): Boolean {
        val desc = Desc(a[index1].value!!, expected1, update1,
            a[index2].value!!, expected2, expected2)
        val ref1 = a[index1].value!!
        val ref2 = a[index2].value!!
        if (!ref1.casValue(ref1, desc)) return false
        if (ref2.getValue() == expected2) {
            ref1.setValue(update1)
            return true
        } else {
            return false
        }
    }
}


enum class DescOutcome {UNDECIDED, SUCCESS, FAIL}
class Desc<E>(val ref1: Ref<E>, val expected1: E, val update1: E,
              val ref2: Ref<E>, val expected2: E, val update2: E) {
    val outcome = atomic(DescOutcome.UNDECIDED)
    fun complete() {
        val update = if (ref2.getValue() == expected2) {
            outcome.value = DescOutcome.SUCCESS
            update1
        }
        else {
            outcome.value = DescOutcome.FAIL
            expected1
        }
        ref1.casValue(this, update)
    }
}

class Ref<E>(init: E) {
    private val _value = atomic<Any?>(init)

    fun getValue() : E {
        while(true) {
            val curVal = _value.value
            if (curVal is Desc<*>) {
                curVal.complete()
            } else {
                return curVal as E
            }
        }
    }

    fun setValue(upd: E) {
        while(true) {
            val curVal = _value.value
            if (curVal is Desc<*>) {
                curVal.complete()
            } else {
                if (_value.compareAndSet(curVal, upd)) {
                    return
                }
            }
        }
    }

    fun casValue(expected: Any?, update: Any?): Boolean {
        return _value.compareAndSet(expected, update)
    }
}