import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }
    init { for (i in 0 until size) a[i] = Ref(initialValue) }
    fun get(index: Int) = a[index].value!!
    fun cas(index: Int, expected: Any?, update: Any?) = a[index].cas(expected, update)
    fun cas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (index1 < index2) {
            val descriptor = Descriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (cas(index1, expected1, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == true
            }
        } else if (index1 > index2) {
            val descriptor = Descriptor(a[index2], expected2, update2, a[index1], expected1, update1)
            if (cas(index2, expected2, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == true
            }
        } else return expected1 == expected2 && cas(index1, expected1, update1.toString().toInt() + 2)
        return false
    }
}

class Descriptor<E> (
    private val a: Ref<E>, private val expectedA: E, private val updateA: E,
    private val b: Ref<E>, private val expectedB: E, private val updateB: E) {
    val outcome: Ref<Boolean?> = Ref(null)
    fun complete() {
        if (if (b.ref.value != this) b.cas(expectedB, this) else true) {
            outcome.ref.compareAndSet(null, true)
            a.ref.compareAndSet(this, updateA)
            b.ref.compareAndSet(this, updateB)
            return
        }
        outcome.ref.compareAndSet(null, false)
        a.ref.compareAndSet(this, expectedA)
        b.ref.compareAndSet(this, expectedB)
    }
}

class Ref<E>(initialValue: E?) {
    val ref: AtomicRef<Any?> = atomic(initialValue)
    var value: E
        get() = ref.loop { if (it is Descriptor<*>) it.complete() else return it as E }
        set(new) { ref.loop {if (it is Descriptor<*>) it.complete() else if (ref.compareAndSet(it, new)) return } }
    fun cas(expect: Any?, update: Any?): Boolean {
        while (true) {
            if (ref.compareAndSet(expect, update)) return true
            val curValue = ref.value
            if (curValue is Descriptor<*>) curValue.complete()
            else if (curValue != expect) return false
        }
    }
}