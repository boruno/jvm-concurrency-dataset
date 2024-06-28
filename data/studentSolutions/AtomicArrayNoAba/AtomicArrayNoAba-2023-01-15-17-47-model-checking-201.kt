import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = Array(size){Ref(initialValue)}

    class Ref(initial: Any?) {
        val v = atomic(initial)
        fun get(): Any? {
            v.loop { cur ->
                when (cur) {
                    is CASNDescriptor -> cur.complete()
                    else -> return cur
                }
            }
        }
        fun casWithHelping(expect: Any?, update: Any?): Boolean {
            v.loop { cur ->
                if (v.compareAndSet(expect, update)) {
                    return true
                } else if (cur is CASNDescriptor) {
                    cur.complete()
                } else if (cur != expect) {
                    return false
                }
            }
        }
        fun cas(expect: Any?, update: Any?): Boolean {
            return v.compareAndSet(expect, update)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int) =
        a[index].get() as E

    fun cas(index: Int, expected: E, update: E) =
        a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,  index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2) {
            return cas(index1, expected1, update1)
        }
        return if (index1 < index2) {
            cas2Ordered(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            cas2Ordered(a[index2], expected2, update2, a[index1], expected1, update1)
        }
    }

    private fun cas2Ordered(object1: Ref, expect1: Any?, update1: Any?,
                            object2: Ref, expect2: Any?, update2: Any?): Boolean {
        val descriptor = CASNDescriptor(object1, expect1, update1, object2, expect2, update2)
        return if (object1.casWithHelping(expect1, descriptor)) {
            descriptor.complete()
            return descriptor.outcome.v.compareAndSet(OUTCOME.SUCCESS, OUTCOME.SUCCESS)
        } else {
            false
        }
    }

    private enum class OUTCOME {
        UNDECIDED,
        FAILED,
        SUCCESS
    }

    private class CASNDescriptor(val object1: Ref, val expect1: Any?, val update1: Any?, val object2: Ref, val expect2: Any?, val update2: Any?) {
        val outcome = Ref(OUTCOME.UNDECIDED)

        fun complete() {
            var result = object2.cas(this, this) || object2.casWithHelping(expect2, this)
            outcome.cas(OUTCOME.UNDECIDED, if (result) OUTCOME.SUCCESS else OUTCOME.FAILED)
            result = outcome.cas(OUTCOME.SUCCESS, OUTCOME.SUCCESS)
            object2.cas(this, if (result) update2 else expect2)
            object1.cas(this, if (result) update1 else expect1)
        }
    }

}