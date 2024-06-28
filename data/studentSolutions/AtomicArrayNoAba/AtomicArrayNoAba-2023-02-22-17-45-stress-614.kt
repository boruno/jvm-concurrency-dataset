import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun getValue(index: Int) = a[index].value!!

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            getValue(index).also {
                if (it == update) {
                    return true
                }
                if (it is Descriptor) {
                    it.complete()
                } else if (it != expected) {
                    return false
                }
            }
            if (a[index].v.compareAndSet(expected, update)) {
                return true
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2) {
            return cas(index1, expected1, (update1 as Int + 1) as E);
        }

        val descriptor: RDCSSDescriptor<E, E>;
        if (index1 < index2) {
            descriptor = RDCSSDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
        } else {
            descriptor = RDCSSDescriptor(a[index2], expected2, update2, a[index1], expected1, update1)
        }
        if (!descriptor.a.cas(descriptor.expectA, descriptor)) {
            return false;
        } else {
            descriptor.complete();
            return descriptor.outcome.value == Outcome.SUCCESS;
        }
    }


}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as T
                }
            }

        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

    fun cas(expect: Any?, update: Any?): Boolean {
        v.loop { cur ->
            if (cur is Descriptor) {
                cur.complete()
            } else if (expect != cur) {
                return false
            } else if (v.compareAndSet(cur, update)) {
                return true
            }
        }
    }

    fun cas(element: AtomicRef<Any?>, expect: Any?, update: Any?): Boolean {
        return v.compareAndSet(expect, update);
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}


class RDCSSDescriptor<A, B>(
    val a: Ref<A>,
    val expectA: A,
    val updateA: A,
    val b: Ref<B>,
    val expectB: B,
    val updateB: B,
    val outcome: Ref<Outcome> = Ref(Outcome.UNDECIDED)
) : Descriptor() {
    override fun complete(): Boolean {
        if (b.v.compareAndSet(expectB, this)) {
            outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        } else {
            outcome.v.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
        }
        return if (outcome.v.value === Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
            true
        } else {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
            false
        }
    }
}

enum class Outcome {
    UNDECIDED, SUCCESS, FAIL
}