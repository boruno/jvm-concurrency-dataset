import AtomicArrayNoAba.Outcome.*
import kotlinx.atomicfu.*
import java.lang.Integer.max
import java.lang.Integer.min

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private enum class Outcome {
        UNDECIDED,
        SUCCESS,
        FAIL,
    }

    private interface Descriptor {
        fun complete()
    }

    private class CAS2Descriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B, val updateB: B,
        val outcome: AtomicRef<Outcome> = atomic(UNDECIDED)
    ) : Descriptor {
        override fun complete() {
            val updateOutcome = if (b.v.compareAndSet(expectB, this)) { SUCCESS } else { FAIL } // Or DCSS instead of CAS
            outcome.compareAndSet(UNDECIDED, updateOutcome)
            if (outcome.value == SUCCESS) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                a.v.compareAndSet(this, expectA)
            }
        }
    }

    private class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop {
                    when(it) {
                        is Descriptor -> it.complete()
                        else -> return it as T
                    }
                }
            }
            set(upd) {
                v.loop {
                    when (it) {
                        is Descriptor -> it.complete()
                        else -> if (v.compareAndSet(it, upd)) return
                    }
                }
            }

        fun cas(expected: T, update: T): Boolean {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> return v.compareAndSet(expected, update)
                }
            }
        }
    }

    private val a = atomicArrayOfNulls<Ref<Any?>>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value as E

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val index11 = min(index1, index2)
        val index22 = max(index1, index2)
        val x = a[index11].value!!
        val descriptor = CAS2Descriptor(x, expected1, update1, a[index22].value!!, expected2, update2)
        if (!a[index11].compareAndSet(x, Ref(descriptor))) {
            return false
        }
        descriptor.complete()
        return descriptor.outcome.value == SUCCESS
    }
}