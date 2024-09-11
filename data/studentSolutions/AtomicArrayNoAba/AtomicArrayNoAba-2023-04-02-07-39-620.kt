import kotlinx.atomicfu.*

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
    fun cas(expect: Any?, update: Any?): Boolean {
        v.loop {
            if (it is Descriptor) it.complete()
            else return it == expect && v.compareAndSet(it, update)
        }
    }
    fun defaultCompareAndSet(expected: Any?, update: Any?): Boolean {
        return v.compareAndSet(expected, update)
    }
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val array: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        array[index].value

    fun cas(index: Int, expected: E, update: Any?) =
        array[index].defaultCompareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2 && expected1 is Int) {
            return cas(index1, expected1, expected1 + 2)
        }
        if (index1 == index2 && expected1 == expected2) {
            return cas(index1, expected1, update1)
        }
        if (index1 == index2) {
            return false
        }
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }

        val descriptor = CAS2Descriptor(array[index1], expected1, update1, array[index2], expected2, update2)
        if (cas(index1, expected1, descriptor)) {
            descriptor.complete()
        }
        return descriptor.outcome.value == Outcome.Success
    }
}


private enum class Outcome { Undecided, Success, Fail }

private class CAS2Descriptor<A, B>(
    val index1: Ref<A>, val expected1: A, val update1: A,
    val index2: Ref<B>, val expected2: B, val update2: B,
) : Descriptor() {
    val outcome = atomic(Outcome.Undecided)
    override fun complete() {
        val newStatus = if (index2.cas(expected2, this) != expected2) Outcome.Fail else Outcome.Success
        outcome.compareAndSet(Outcome.Undecided, newStatus)
        if (outcome.value == Outcome.Fail) {
            index1.v.compareAndSet(this, expected1)
            index2.v.compareAndSet(this, expected2)
            return
        }
        outcome.compareAndSet(Outcome.Undecided, Outcome.Success)
        index1.v.compareAndSet(this, update1)
        index2.v.compareAndSet(this, update2)
    }
}