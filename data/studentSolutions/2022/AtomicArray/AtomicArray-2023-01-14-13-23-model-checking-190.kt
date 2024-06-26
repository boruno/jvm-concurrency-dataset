import kotlinx.atomicfu.*

/**
 * @author : Кулешова Екатерина
 */

class AtomicArray<E: Any>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    fun get(index: Int) =
        a[index].value

    fun set(index: Int, value: E) {
        a[index].value = value
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].cas(expected, update)

    fun cas2(
        index1: Int, expect1: E, update1: E,
        index2: Int, expect2: E, update2: E,
    ): Boolean {
        if (index1 == index2) {
            return checkValidEquals(index1, expect1, update1, expect2, update2)
        }

        val descriptor = if (index1 < index2) {
            DescriptorCAS2(
                a[index1], expect1, update1,
                a[index2], expect2, update2,
            )
        } else {
            DescriptorCAS2(
                a[index2], expect2, update2,
                a[index1], expect1, update1,
            )
        }

        descriptor.init()
        if (descriptor.outcome.value == Outcome.UNDECIDED) {
            descriptor.complete()
        }
        return descriptor.outcome.value == Outcome.SUCCESS
    }

    private fun checkValidEquals(
        index: Int, expect1: E, update1: E,
                    expect2: E, update2: E,
    ): Boolean {
        if (expect1 != expect2) return false
//        if (update1 != update2) return false
        return cas(index, expect2, update2)
    }
}

private abstract class Descriptor {
    abstract fun complete()
}

private class DescriptorCAS2<E : Any>(
    val a: Ref<E>, val expectA: E, val updateA: E,
    val b: Ref<E>, val expectB: E, val updateB: E,
) : Descriptor() {
    val outcome: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)

    fun init() {
        a.cas(expectA, this)

        if (a.rawGet() != this) {
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
        }
    }

    override fun complete() {
        b.cas(expectB, this)

        if (b.rawGet() == this) {
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            a.cas(this, updateA)
            b.cas(this, updateB)
        } else {
            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            a.cas(this, expectA)
        }
    }
}

private class Ref<E : Any>(initial: E) {
    private val rawValue: AtomicRef<Any> = atomic(initial)
    var value: E
        get() = rawValue.loop { cur ->
            when (cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
        set(update) {
            rawValue.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (rawValue.compareAndSet(cur, update)) return
                }
            }
        }

    fun cas(expect: Any, update: Any) =
        rawValue.compareAndSet(expect, update)

    fun rawGet() =
        rawValue.value
}

enum class Outcome {
    UNDECIDED,
    SUCCESS,
    FAIL,
}
