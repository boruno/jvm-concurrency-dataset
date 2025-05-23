import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = Array<Ref<Any>>(size) { Ref() }

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E =
        a[index].value!! as E

    fun cas(index: Int, expected: E, update: E) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        require(index1 != index2)

        if (index1 > index2) {
            cas2(
                index2, expected2, update2,
                index1, expected1, update1
            )
        }

        return casN(
            a[index1], expected1 as Any, update1 as Any,
            a[index2], expected2 as Any, update2 as Any
        )
    }


}

fun dcss(
    a: Ref<Any>, expectA: Any, updateA: Any,
    b: Ref<Any>, expectB: Any
): Boolean {
    a.value
    b.value
    val descriptor = DcssDescriptor(a, expectA, updateA, b, expectB)
    if (a.v.compareAndSet(expectA, descriptor)) {
        descriptor.complete()
        return descriptor.result.value == Outcome.SUCCESS
    }
    return false
}

fun casN(
    a: Ref<Any>, expectA: Any, updateA: Any,
    b: Ref<Any>, expectB: Any, updateB: Any
): Boolean {
    a.value
    b.value
    val descriptor = Cas2Descriptor(
        a, expectA, updateA,
        b, expectB, updateB
    )
    if (a.v.compareAndSet(expectA, descriptor)) {
        descriptor.complete()
        return descriptor.result.value == Outcome.SUCCESS
    }
    return false
}

class DcssDescriptor(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any,
) {
    val result: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)
    fun complete() {
        if (expectB == b.value)
            result.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
        else
            result.compareAndSet(Outcome.UNDECIDED, Outcome.FAILED)

        val update = if (result.value == Outcome.SUCCESS) updateA else expectA

        a.v.compareAndSet(this, update)
    }
}

class Cas2Descriptor(
    val a: Ref<Any>, val expectA: Any, val updateA: Any,
    val b: Ref<Any>, val expectB: Any, val updateB: Any,
) {
    val result: Ref<Any> = Ref()

    init {
        result.value = Outcome.UNDECIDED
    }

    fun complete() {
        if (dcss(b, expectB, this, result, Outcome.UNDECIDED)) {
            result.value = Outcome.SUCCESS
        } else {
            result.value = Outcome.FAILED
        }

        if (result.value == Outcome.SUCCESS) {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        } else {
            a.v.compareAndSet(this, expectA)
        }
    }
}

enum class Outcome {
    SUCCESS, FAILED, UNDECIDED
}

class Ref<E> {
    val v = atomic<Any?>(null)
    var value: E?
        get() {
            while (true) {
                when (val it = v.value) {
                    is DcssDescriptor -> it.complete()
                    is Cas2Descriptor -> it.complete()
                    else -> return it as E?
                }
            }
        }
        set(value) {
            v.value = value
        }
}