import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int) = a[index].get()
    fun set(index: Int, upd: E) {
        a[index].set(upd)
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        return a[index].cas(expected, update)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) {
                cas(index1, expected1, (expected1.toString().toInt() + 2) as E)
            } else {
                false
            }
        }

        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }

        val desc = CDescriptor(a[index1], expected1, update1, a[index2], expected2, update2)
        return if (a[index1].cas(expected1, desc)) {
            desc.complete()
        } else {
            false
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}


class CDescriptor<E>(
    val a: Ref<E>, val expectA: E, val updateA: E,
    val b: Ref<E>, val expectB: E, val updateB: E,
    private val outcome: Ref<StateType> = Ref(StateType.ACTIVE),
) : Descriptor() {
    override fun complete(): Boolean {
        var checker = true
        if (b.v.value != this) {
            checker = b.cas(expectB, this)
        }
        if (checker) {
            outcome.v.compareAndSet(StateType.ACTIVE, StateType.SUCCESSFUL)
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            outcome.v.compareAndSet(StateType.ACTIVE, StateType.FAILED)
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        }
        return outcome.v.value === StateType.SUCCESSFUL
    }
}


class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)
    fun set(upd: E) {
        while (true) {
            val cur = v.value
            when (cur) {
                is Descriptor -> cur.complete()
                else -> if (v.compareAndSet(cur, upd))
                    return
            }
        }
    }

    fun get(): E {
        while (true) {
            val cur = v.value
            when (cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
    }

    fun cas(expected: Any?, update: Any?): Boolean {
        while (true) {
            if (v.compareAndSet(expected, update)) {
                return true
            }
            val current = v.value
            if (current is Descriptor) {
                current.complete()
            }
            if (current != expected) {
                return false
            }
        }
    }
}
enum class StateType {
    ACTIVE, SUCCESSFUL, FAILED
}

