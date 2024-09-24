import Outcome.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.loop
import java.util.concurrent.atomic.AtomicReference

private enum class Outcome {
    UNDECIDED,
    SUCCESS,
    FAIL,
}

private fun <A, B> dcss(a: Ref<A>, expectA: A, updateA: Any,
                        b: AtomicReference<B>, expectB: B): Boolean {
    val descriptor = DCSSDescriptor(a, expectA, updateA, b, expectB)
    if (!a.v.compareAndSet(expectA, descriptor)) {
        return false
    }
    descriptor.complete()
    return descriptor.outcome.value == SUCCESS
}

private class DCSSDescriptor<A, B>(
    val a: Ref<A>, val expectA: A, val updateA: Any,
    val b: AtomicReference<B>, val expectB: B,
    val outcome: AtomicRef<Outcome> = atomic(UNDECIDED)
) : Descriptor {
    override fun complete() {
        val updateOutcome = if (b.get() == expectB) {
            SUCCESS
        } else {
            FAIL
        }
        outcome.compareAndSet(UNDECIDED, updateOutcome)
        if (outcome.value == SUCCESS) {
            a.v.compareAndSet(this, updateA)
        } else {
            a.v.compareAndSet(this, expectA)
        }
    }
}

private interface Descriptor {
    fun complete()
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

class AtomicArray<E>(size: Int, initialValue: E) {
    private class CAS2Descriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B, val updateB: B,
        val outcome: AtomicReference<Outcome> = AtomicReference(UNDECIDED)
    ) : Descriptor {
        override fun complete() {
//            val updateOutcome = if (b.v.compareAndSet(expectB, this)) { SUCCESS } else { FAIL } // Or DCSS instead of CAS
            val updateOutcome = if (dcss(b, expectB, this, outcome, UNDECIDED)) { SUCCESS } else { FAIL }
            outcome.compareAndSet(UNDECIDED, updateOutcome)
            if (outcome.get() == SUCCESS) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                a.v.compareAndSet(this, expectA)
                b.v.compareAndSet(this, expectB)
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
        if (index1 == index2) {
            return cas(index1, expected1, (expected1 as Int + 2) as E)
        }
        val index11: Int
        val index22: Int
        val expected11: E
        val expected22: E
        val update11: E
        val update22: E
        if (index1 < index2) {
            index11 = index1
            index22 = index2
            expected11 = expected1
            expected22 = expected2
            update11 = update1
            update22 = update2
        } else {
            index11 = index2
            index22 = index1
            expected11 = expected2
            expected22 = expected1
            update11 = update2
            update22 = update1
        }
        val descriptor = CAS2Descriptor(a[index11].value!!, expected11, update11, a[index22].value!!, expected22, update22)
        if (!a[index11].value!!.v.compareAndSet(expected11, descriptor)) {
            return false
        }
        descriptor.complete()
        return descriptor.outcome.get() == SUCCESS
    }
}