import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array(size) { Ref(initialValue) }

    fun get(index: Int): E = a[index].value

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

            return cas(index1, expected1, (expected1.toString().toInt() + 2) as E)
        }

        val descriptor = CASNDescriptor(
            a[index1], expected1, update1,
            a[index2], expected2, update2
        )

        return if (a[index1].cas(expected1, descriptor)) {
            descriptor.complete()
        } else {
            false
        }
    }
}

abstract class Descriptor {
    abstract fun complete(): Boolean
}

class CASNDescriptor<A, B> (
    private val a: Ref<A>, private val expectA: A, private val updateA: A,
    private val b: Ref<B>, private val expectB: B, private val updateB: B,
    private val outcome: Ref<Outcome> = Ref(Outcome.UNDECIDED)
) : Descriptor() {
    override fun complete(): Boolean {
        if (b.cas(expectB, this)) {
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


//    fun cas(expected: T, update: T): Boolean {
//        return casCommon(expected, update)
//    }
//
//    fun casDescriptor(expected: T, update: Descriptor): Boolean {
//        return casCommon(expected, update)
//    }
//
//    private fun casCommon(expected: Any?, update: Any?): Boolean {
//        while (true) {
//            if (v.compareAndSet(expected, update)) {
//                return true
//            }
//
//            val act = v.value
//
//            if (act is Descriptor) {
//                if (act === update) {
//                    return true
//                }
//
//                act.complete()
//            } else if (act !== expected) {
//                return false
//            }
//        }
//    }

    fun cas(expected: T, update: Any?): Boolean {
        v.loop {
            when (it) {
                is Descriptor -> it.complete()
                else -> return v.compareAndSet(expected, update)
            }
        }
    }
}

enum class Outcome {
    UNDECIDED,
    SUCCESS,
    FAIL
}