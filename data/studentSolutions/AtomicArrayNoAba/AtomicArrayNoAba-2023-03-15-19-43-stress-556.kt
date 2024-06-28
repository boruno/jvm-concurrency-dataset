import kotlinx.atomicfu.*

enum class Outcome {
    UND, SUC, FAIL
}

abstract class Descriptor { abstract fun complete() }

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { IntermediateValue(initialValue) }
    fun get(index: Int) = a[index].value!!
    fun cas(index: Int, expected: E, update: E): Boolean { return a[index].cas(expected, update) }
    fun cas2(i1: Int, expected1: E, update1: E, i2: Int, expected2: E, update2: E): Boolean {
        if (i1 == i2) {
            if (expected1 == expected2) {
                return cas(i1, expected1, update1)
            }
            return false
        }
        if (i1 >= i2) {
            val descriptor = DescriptorImpl(a[i2], expected2, update2, a[i1], expected1, update1)
            if (a[i2].cas(expected2, descriptor)) {
                descriptor.complete()
                return descriptor.status.value == Outcome.SUC
            }
        } else {
            val descriptor: DescriptorImpl<E> = DescriptorImpl(a[i1], expected1, update1, a[i2], expected2, update2)
            if (a[i1].cas(expected1, descriptor)) {
                descriptor.complete()
                return descriptor.status.value == Outcome.SUC
            }
        }
        return false
    }
}

class DescriptorImpl<E>(
    private val a: IntermediateValue<E>,
    private val expectA: E,
    private val updateA: E,
    private val b: IntermediateValue<E>,
    private val expectB: E,
    private val updateB: E
) : Descriptor() {
    val status: IntermediateValue<Outcome> = IntermediateValue(Outcome.UND)
    override fun complete() {
        if (if(b.v.value != this) b.cas(expectB, this) else true) {
            status.v.compareAndSet(Outcome.UND, Outcome.SUC)
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            status.v.compareAndSet(Outcome.UND, Outcome.FAIL)
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        }
    }
}

class IntermediateValue<E>(initialValue: E) {
    val v: AtomicRef<Any?> = atomic(initialValue)
    var value: E
        get() = v.loop {
            if (it !is Descriptor) return it as E else it.complete()
        }
        set(update) {
            v.loop {
                if (it is Descriptor) {
                    it.complete()
                } else if (v.compareAndSet(it, update)) {
                    return
                }
            }
        }
    fun cas(expect: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expect, update)) return true
            val cur: Any? = v.value
            if (cur is Descriptor) cur.complete()
            else if (cur != expect) return false
        }
    }
}