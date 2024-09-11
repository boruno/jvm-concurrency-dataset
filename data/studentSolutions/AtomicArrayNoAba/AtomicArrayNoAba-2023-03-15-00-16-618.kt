import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E): Boolean {
        return a[index].cas(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2) {
                return cas(index1, expected1, update2 as E)
            }

            return false
        }

        if (index1 > index2) {
            val descriptor = MWCASDescriptor(a[index2], expected2, update2, a[index1], expected1, update1)
            if (a[index2].cas(expected2, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == Outcome.SUCCESS
            }
        } else {
            val descriptor = MWCASDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
            if (a[index1].cas(expected1, descriptor)) {
                descriptor.complete()
                return descriptor.outcome.value == Outcome.SUCCESS
            }
        }

        return false
    }
}

class Ref<E>(initial: E) {
    val v: AtomicRef<Any?> = atomic(initial)

    var value: E
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(value) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> {
                        if (v.compareAndSet(cur, value)) {
                            return
                        }
                    }
                }
            }
        }

    fun cas(expect: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expect, update)) {
                return true
            }

            val curValue = v.value
            if (curValue is Descriptor) {
                curValue.complete()
            } else {
                if (curValue != expect) {
                    return false
                }
            }
        }
    }
}

abstract class Descriptor {
    abstract fun complete()
}

class MWCASDescriptor<E>(
    val a: Ref<E>, val expectA: E, val updateA: E,
    val b: Ref<E>, val expectB: E, val updateB: E
) : Descriptor() {
    val outcome: Ref<Outcome?> = Ref(null)

    override fun complete() {
        val updA: Boolean
        if (b.v.value == this) {
            updA = true
        } else {
            updA = b.cas(expectB, this)
        }

        if (updA) {
            outcome.v.compareAndSet(null, Outcome.SUCCESS)
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            outcome.v.compareAndSet(null, Outcome.FAILED)
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        }
    }
}

enum class Outcome {
    SUCCESS,
    FAILED
}