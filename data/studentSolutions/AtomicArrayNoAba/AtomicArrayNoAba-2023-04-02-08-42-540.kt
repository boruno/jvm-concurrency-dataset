import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init { for (i in 0 until size) a[i] = Ref(initialValue) }

    fun get(index: Int) = a[index]!!.value

    fun cas(index: Int, expected: E, update: E) = a[index]!!.v.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2 && expected1 is Int) return cas(index1, expected1, (expected1 + 2) as E)
        if (index1 == index2 && expected1 == expected2) return cas(index1, expected1, update1)

        if (index1 == index2) return false

        val descriptor =
            if (index1 > index2) CAS2Descriptor(a[index2]!!, expected2, update2, a[index1]!!, expected1, update1)
            else CAS2Descriptor(a[index1]!!, expected1, update1, a[index2]!!, expected2, update2)

        val expected = if (index1 > index2) expected2 else expected1

        if (a[minOf(index1, index2)]!!.v.compareAndSet(expected, descriptor)) descriptor.complete()
        return descriptor.outcome.value == Outcome.Success
    }
}

private abstract class Descriptor {
    abstract fun complete()
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)
    var value: T
        get() { v.loop { if (it is Descriptor) it.complete() else return it as T } }
        set(new) {
            v.loop {
                if (it is Descriptor) it.complete()
                else if (v.compareAndSet(it, new)) return
            }
        }
//    fun cas(expect: Any?, update: Any?): Boolean {
//        v.loop {
//            if (it is Descriptor) it.complete()
//            else return it == expect && v.compareAndSet(it, update)
//        }
//    }
    fun cas(expected: Any?, update: Any?): Boolean {
        v.loop {
            if (it is Descriptor) it.complete()
            else if (it == expected) { if (v.compareAndSet(it, update)) return true }
            else return false
        }
    }
}

private enum class Outcome { Undecided, Success, Fail }

private class CAS2Descriptor<A, B>(
    val aRef: Ref<A>, val aExpect: A, val aUpdate: A,
    val bRef: Ref<B>, val bExpect: B, val bUpdate: B,
) : Descriptor() {
    val outcome = atomic(Outcome.Undecided)
    override fun complete() {
        val newStatus = if (bRef.cas(bExpect, this)) Outcome.Success else Outcome.Fail
        outcome.compareAndSet(Outcome.Undecided, newStatus)
        if (outcome.value == Outcome.Fail) {
            aRef.v.compareAndSet(this, aExpect)
            bRef.v.compareAndSet(this, bExpect)
        } else {
            outcome.compareAndSet(Outcome.Undecided, Outcome.Success)
            aRef.v.compareAndSet(this, aUpdate)
            bRef.v.compareAndSet(this, bUpdate)
        }
    }
}