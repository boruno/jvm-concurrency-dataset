import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) = a[index].value!!

    fun cas(index: Int, expected: E, update: E) = a[index].cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 > index2) {
            return cas2(
                index2, expected2, update2,
                index1, expected1, update1
            )
        }

        if (index1 == index2) {
            if (expected1 !== expected2) {
                return false
            }

            return cas(index1, expected1, update2)
        }

        val descriptor = CASNDescriptor(
            a[index1], expected1, update1,
            a[index2], expected2, update2
        )

        return if (a[index1].casDescriptor(expected1, descriptor)) {
            descriptor.complete()
        } else {
            false
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class DCSSDescriptor<A, T, B>(
    private val a: Ref<A>, private val expectA: A, private var updateA: T,
    private val b: Ref<B>, private val expectB: B,
    private val outcome: AtomicRef<Condition> = atomic(Condition.PROCESS)
) : Descriptor() {
    override fun complete(): Boolean {
        if (b.value === expectB) {
            outcome.compareAndSet(Condition.PROCESS, Condition.SUCCESS)
        } else {
            outcome.compareAndSet(Condition.PROCESS, Condition.FAIL)
        }

        return if (outcome.value === Condition.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            true
        } else {
            a.v.compareAndSet(this, expectA)
            false
        }
    }
}

class CASNDescriptor<A, B> (
    private val a: Ref<A>, private val expectA: A, private val updateA: A,
    private val b: Ref<B>, private val expectB: B, private val updateB: B,
    private val outcome: Ref<Condition> = Ref(Condition.PROCESS)
) : Descriptor() {
    override fun complete(): Boolean {
        if (b.v.value === this || b.dcssDescriptor(expectB, this, outcome, Condition.PROCESS)) {
            outcome.v.compareAndSet(Condition.PROCESS, Condition.SUCCESS)
        } else {
            outcome.v.compareAndSet(Condition.PROCESS, Condition.FAIL)
        }

        return if (outcome.v.value === Condition.SUCCESS) {
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

@Suppress("UNCHECKED_CAST")
class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)

    var value: T
        get() {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> return it as T
                }
            }
        }
        set(newValue: T) {
            v.loop {
                when (it) {
                    is Descriptor -> it.complete()
                    else -> if (v.compareAndSet(it, newValue)) return
                }
            }
        }


    fun cas(expected: T, update: T): Boolean {
        return casCommon(expected, update)
    }

    fun casDescriptor(expected: T, update: Descriptor): Boolean {
        return casCommon(expected, update)
    }

    private fun casCommon(expected: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expected, update)) {
                return true
            }

            val act = v.value

            if (act is Descriptor) {
                act.complete()
            } else {
                if (act != expected) {
                    return false
                }
            }
        }
    }

    fun <B> dcssDescriptor(expectA: T, updateA: Descriptor, b: Ref<B>, expectB: B): Boolean {
        val descriptor = DCSSDescriptor(this, expectA, updateA, b, expectB)

        if (!casDescriptor(expectA, descriptor)) {
            return false
        }

        return descriptor.complete()
    }
}

enum class Condition {
    PROCESS,
    SUCCESS,
    FAIL
}