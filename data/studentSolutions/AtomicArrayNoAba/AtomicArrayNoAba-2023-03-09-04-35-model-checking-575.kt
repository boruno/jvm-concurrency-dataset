import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }
//    init { for (i in 0 until size) a[i] = Ref(initialValue) }

    fun get(index: Int) = a[index].value!!

    fun cas(index: Int, expected: E, update: E) = a[index].cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        var i1 = index1; var i2 = index2; var e1 = expected1; var e2 = expected2; var u1 = update1; var u2 = update2
        if (i1 > i2) i1 = i2.also{ i2 = i1 }; e1 = e2.also{ e2 = e1 }; u1 = u2.also { u2 = u1 }
        val descriptor = Descriptor(a[i1], e1, u1, a[i2], e2, u2)
        if (a[i1].cas(e1, descriptor)) {
            descriptor.complete()
            return descriptor.outcome.value == true
        }
        return false
    }
}

class Descriptor<E> (
    private val a: Ref<E>, private val expectedA: E, private val updateA: E,
    private val b: Ref<E>, private val expectedB: E, private val updateB: E) {
    val outcome: Ref<Boolean?> = Ref(null)
    fun complete() {
        val fl = if (b.ref.value != this) b.cas(expectedB, this) else true
        outcome.ref.compareAndSet(null, fl)
        if (fl) {
            a.ref.compareAndSet(this, updateA)
            b.ref.compareAndSet(this, updateB)
            return
        }
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